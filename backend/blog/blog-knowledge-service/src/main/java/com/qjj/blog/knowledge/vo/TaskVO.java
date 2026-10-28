package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 任务出参（api-design.md §2.7 TaskVO）
 */
@Getter
@Setter
public class TaskVO {

    private String id;

    private String taskType;

    private String docId;

    private String docTitle;

    private Integer status;

    private Integer retryCount;

    private Integer maxRetry;

    private String failReason;

    private String startTime;

    private String endTime;

    private String createTime;
}
