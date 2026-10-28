package com.qjj.blog.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库任务（knowledge_task；table-design.md §2.6）。
 * 任务表 = doc/操作级调度与留痕；chunk.embed_status = chunk 级真相源；二者仅 task.doc_id 关联。
 */
@Getter
@Setter
@TableName("knowledge_task")
public class KnowledgeTask extends KnowledgeBaseEntity {

    /** INGEST / REVECTORIZE / INVALIDATE / FULL_REINDEX / SYNC_ARTICLES */
    private String taskType;

    /** 目标文档；全局任务（FULL_REINDEX / SYNC_ARTICLES）为 0 */
    private Long docId;

    /** 任务参数 JSON（source/sourceId/action/contentVersion 等） */
    private String payload;

    /** 0 PENDING / 1 RUNNING / 2 SUCCESS / 3 FAILED / 4 DEAD */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 重试上限（指数退避最多 3 次） */
    private Integer maxRetry;

    /** 下次可执行时间（退避留痕/重跑依据） */
    private LocalDateTime nextRetryTime;

    /** 失败原因 */
    private String failReason;

    /** 本次执行开始 */
    private LocalDateTime startTime;

    /** 本次执行结束 */
    private LocalDateTime endTime;
}
