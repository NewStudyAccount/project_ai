package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.concurrent.KnowledgeExecutorConfiguration;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.knowledge.dto.TaskPageQuery;
import com.qjj.blog.knowledge.entity.KnowledgeDoc;
import com.qjj.blog.knowledge.entity.KnowledgeTask;
import com.qjj.blog.knowledge.enums.KnowledgeTaskStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeTaskTypeEnum;
import com.qjj.blog.knowledge.mapper.KnowledgeDocMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeTaskMapper;
import com.qjj.blog.knowledge.service.KnowledgeTaskService;
import com.qjj.blog.knowledge.vo.TaskVO;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 知识库任务服务实现（table-design.md §2.6 任务生命周期）：
 * <ul>
 *   <li>入队去重：同 (doc_id, task_type) 已存在 PENDING/RUNNING 则跳过（应用层保证）；</li>
 *   <li>立即投递 knowledgeAiExecutor（framework 线程池）；池满 203103；</li>
 *   <li>执行：PENDING → RUNNING → SUCCESS；失败指数退避重试（5s×2^n）≤ max_retry；超限 DEAD；</li>
 *   <li>启动自愈：遗留 RUNNING 置回 PENDING（进程重启后任务不卡死）。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeTaskServiceImpl implements KnowledgeTaskService, ApplicationRunner {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long BASE_BACKOFF_MS = 5_000L;

    private final KnowledgeTaskMapper taskMapper;
    private final KnowledgeDocMapper docMapper;
    private final IdGenerator idGenerator;
    private final ExecutorService knowledgeAiExecutor;
    private final TaskHandler handler;

    public KnowledgeTaskServiceImpl(KnowledgeTaskMapper taskMapper,
                                    KnowledgeDocMapper docMapper,
                                    IdGenerator idGenerator,
                                    @Qualifier(KnowledgeExecutorConfiguration.KNOWLEDGE_AI_EXECUTOR)
                                    ExecutorService knowledgeAiExecutor,
                                    TaskHandler handler) {
        this.taskMapper = taskMapper;
        this.docMapper = docMapper;
        this.idGenerator = idGenerator;
        this.knowledgeAiExecutor = knowledgeAiExecutor;
        this.handler = handler;
    }

    @Override
    public Long enqueue(String taskType, Long docId, String payload) {
        Long existing = taskMapper.selectCount(new LambdaQueryWrapper<KnowledgeTask>()
                .eq(KnowledgeTask::getTaskType, taskType)
                .eq(KnowledgeTask::getDocId, docId)
                .in(KnowledgeTask::getStatus,
                        KnowledgeTaskStatusEnum.PENDING.getCode(),
                        KnowledgeTaskStatusEnum.RUNNING.getCode()));
        if (existing != null && existing > 0) {
            return null;
        }
        KnowledgeTask task = new KnowledgeTask();
        task.setId(idGenerator.nextId());
        task.setTaskType(taskType);
        task.setDocId(docId);
        task.setPayload(payload);
        task.setStatus(KnowledgeTaskStatusEnum.PENDING.getCode());
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setFailReason("");
        taskMapper.insert(task);
        dispatch(task);
        return task.getId();
    }

    /** 供重跑与入队复用的直接投递 */
    public void dispatch(KnowledgeTask task) {
        try {
            knowledgeAiExecutor.execute(() -> run(task.getId()));
        } catch (RejectedExecutionException e) {
            // 队列满：任务留在 PENDING（下次重跑/重启自愈可再执行），业务侧按 203103 提示
            log.warn("knowledgeAiExecutor 队列满，任务留待重试 taskId={}", task.getId());
            taskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeTask>()
                    .set(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.PENDING.getCode())
                    .eq(KnowledgeTask::getId, task.getId()));
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_TASK_QUEUE_FULL);
        }
    }

    /** 任务执行（含指数退避重试） */
    void run(Long taskId) {
        // 抢占：仅 PENDING 可转 RUNNING（重跑/重复投递互斥）
        int claimed = taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeTask>()
                        .set(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.RUNNING.getCode())
                        .set(KnowledgeTask::getStartTime, LocalDateTime.now())
                        .eq(KnowledgeTask::getId, taskId)
                        .eq(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.PENDING.getCode()));
        if (claimed == 0) {
            return;
        }
        KnowledgeTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 3 : task.getMaxRetry();
        while (true) {
            try {
                handler.execute(task);
                finish(task, KnowledgeTaskStatusEnum.SUCCESS.getCode(), "");
                return;
            } catch (Exception e) {
                retryCount++;
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("knowledge task failed id={} type={} attempt={}/{}: {}",
                        taskId, task.getTaskType(), retryCount, maxRetry + 1, reason);
                if (retryCount > maxRetry) {
                    task.setRetryCount(retryCount);
                    finish(task, KnowledgeTaskStatusEnum.DEAD.getCode(), truncate(reason));
                    return;
                }
                long backoff = BASE_BACKOFF_MS * (1L << (retryCount - 1));
                task.setRetryCount(retryCount);
                task.setFailReason(truncate(reason));
                task.setNextRetryTime(LocalDateTime.now().plusSeconds(backoff / 1000));
                taskMapper.updateById(task);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    finish(task, KnowledgeTaskStatusEnum.FAILED.getCode(), truncate(reason));
                    return;
                }
            }
        }
    }

    private void finish(KnowledgeTask task, int status, String reason) {
        task.setStatus(status);
        task.setFailReason(reason);
        task.setEndTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public TaskVO rerun(String id) {
        KnowledgeTask task;
        try {
            task = taskMapper.selectById(Long.valueOf(id));
        } catch (NumberFormatException e) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_TASK_NOT_FOUND);
        }
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_TASK_NOT_FOUND);
        }
        if (!KnowledgeTaskStatusEnum.FAILED.getCode().equals(task.getStatus())
                && !KnowledgeTaskStatusEnum.DEAD.getCode().equals(task.getStatus())) {
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_TASK_STATE_INVALID);
        }
        task.setStatus(KnowledgeTaskStatusEnum.PENDING.getCode());
        task.setRetryCount(0);
        task.setFailReason("");
        task.setNextRetryTime(null);
        task.setStartTime(null);
        task.setEndTime(null);
        taskMapper.updateById(task);
        dispatch(task);
        return toVO(task);
    }

    @Override
    public PageResult<TaskVO> page(TaskPageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        LambdaQueryWrapper<KnowledgeTask> wrapper = new LambdaQueryWrapper<>();
        if (query.getTaskType() != null && !query.getTaskType().isBlank()) {
            wrapper.eq(KnowledgeTask::getTaskType, query.getTaskType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(KnowledgeTask::getStatus, query.getStatus());
        }
        if (query.getDocId() != null) {
            wrapper.eq(KnowledgeTask::getDocId, query.getDocId());
        }
        wrapper.orderByDesc(KnowledgeTask::getCreateTime).orderByDesc(KnowledgeTask::getId);
        Page<KnowledgeTask> page = taskMapper.selectPage(new Page<>(current, size), wrapper);
        List<TaskVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    /** 启动自愈：遗留 RUNNING 置回 PENDING（table-design.md §6.5） */
    @Override
    public void run(ApplicationArguments args) {
        int healed = taskMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeTask>()
                        .set(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.PENDING.getCode())
                        .eq(KnowledgeTask::getStatus, KnowledgeTaskStatusEnum.RUNNING.getCode()));
        if (healed > 0) {
            log.info("knowledge task 自愈：{} 个遗留 RUNNING 任务已置回 PENDING（可经管理端重跑）", healed);
        }
    }

    @PreDestroy
    void shutdown() {
        // 线程池归 framework 管理，此处不 shutdown（共享池，随进程退出）
    }

    private String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private TaskVO toVO(KnowledgeTask task) {
        TaskVO vo = new TaskVO();
        vo.setId(String.valueOf(task.getId()));
        vo.setTaskType(task.getTaskType());
        vo.setDocId(String.valueOf(task.getDocId()));
        KnowledgeDoc doc = task.getDocId() == null || task.getDocId() == 0 ? null : docMapper.selectById(task.getDocId());
        vo.setDocTitle(doc == null ? "" : doc.getTitle());
        vo.setStatus(task.getStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setFailReason(task.getFailReason());
        vo.setStartTime(task.getStartTime() == null ? null : task.getStartTime().format(TIME));
        vo.setEndTime(task.getEndTime() == null ? null : task.getEndTime().format(TIME));
        vo.setCreateTime(task.getCreateTime() == null ? null : task.getCreateTime().format(TIME));
        return vo;
    }

    /** 任务处理器 SPI（VectorizeService / InvalidateHandler / FullReindexHandler 实现） */
    public interface TaskHandler {
        void execute(KnowledgeTask task) throws Exception;
    }
}
