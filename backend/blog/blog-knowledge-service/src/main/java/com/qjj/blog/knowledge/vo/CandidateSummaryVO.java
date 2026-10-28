package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 候选统计出参（api-design.md §1.2）
 */
@Getter
@Setter
public class CandidateSummaryVO {

    private long total;

    private long uningested;

    private long ingested;

    private long stale;

    private long invalid;
}
