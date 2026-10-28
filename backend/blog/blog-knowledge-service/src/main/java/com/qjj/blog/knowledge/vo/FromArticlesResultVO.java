package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 从文章入库出参（api-design.md §1.4 批量结果明细）
 */
@Getter
@Setter
public class FromArticlesResultVO {

    private int submitted;

    private int skipped;

    private int rejected;

    private List<Accepted> accepted = new ArrayList<>();

    private List<Skipped> skippedItems = new ArrayList<>();

    private List<Rejected> rejectedItems = new ArrayList<>();

    @Getter
    @Setter
    public static class Accepted {
        private String articleId;
        private String docId;
        private String taskId;
        /** true=复用既有 doc 行（重建） */
        private boolean reused;
    }

    @Getter
    @Setter
    public static class Skipped {
        private String articleId;
        /** ALREADY_INGESTED | IN_PROGRESS */
        private String reason;
    }

    @Getter
    @Setter
    public static class Rejected {
        private String articleId;
        /** NOT_PUBLISHED | NO_CONTENT | NOT_FOUND */
        private String reason;
        private int code;
    }
}
