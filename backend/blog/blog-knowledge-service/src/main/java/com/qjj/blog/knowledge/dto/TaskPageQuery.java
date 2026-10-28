package com.qjj.blog.knowledge.dto;

import com.qjj.blog.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 任务分页入参（api-design.md §2.7：taskType / status / docId）
 */
@Getter
@Setter
public class TaskPageQuery extends PageQuery {

    private String taskType;

    private Integer status;

    private Long docId;
}
