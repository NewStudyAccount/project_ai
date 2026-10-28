package com.qjj.blog.knowledge.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.knowledge.dto.TaskPageQuery;
import com.qjj.blog.knowledge.entity.KnowledgeTask;
import com.qjj.blog.knowledge.vo.TaskVO;

/**
 * 知识库任务服务（knowledge_task；table-design.md §2.6）：
 * 入队（应用层去重）→ 立即投递 knowledgeAiExecutor → 重试退避 ≤max_retry → FAILED/DEAD → 管理端重跑。
 */
public interface KnowledgeTaskService {

    /**
     * 入队并立即投递（同 (docId, taskType) 已有 PENDING/RUNNING 时跳过，返回 null）。
     *
     * @throws com.qjj.blog.common.exception.BizException 队列满 203103
     */
    Long enqueue(String taskType, Long docId, String payload);

    /** 重跑失败任务（仅 FAILED/DEAD；重置 status=0/retryCount=0 重新投递；203104/203105） */
    TaskVO rerun(String id);

    PageResult<TaskVO> page(TaskPageQuery query);
}
