package com.qjj.blog.knowledge.enums;

/**
 * 入库状态（api-design.md §0.2 ingestState；判定唯一规则见 table-design.md §2.7）。
 * 接口透出字符串 code，前端按 code 映射文案。
 */
public final class IngestStateEnum {

    public static final String UNINGESTED = "UNINGESTED";
    public static final String INGESTED = "INGESTED";
    public static final String STALE = "STALE";
    public static final String INVALID = "INVALID";

    private IngestStateEnum() {
    }
}
