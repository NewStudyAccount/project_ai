package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统计出参（api-design.md §2.8；ES 不可用时 esDocCount=null/esHealthy=false，接口仍 200）
 */
@Getter
@Setter
public class KnowledgeStatsVO {

    private long docTotal;

    /** {pending, vectorizing, ready, failed, invalid} */
    private Map<String, Long> docByStatus = new LinkedHashMap<>();

    private long chunkTotal;

    /** READY 块占比（0–1，chunkTotal=0 时为 0） */
    private double chunkReadyRatio;

    private Long esDocCount;

    private Boolean esHealthy;

    private long taskFailed;

    private Map<String, Long> providerCalls = new LinkedHashMap<>();
}
