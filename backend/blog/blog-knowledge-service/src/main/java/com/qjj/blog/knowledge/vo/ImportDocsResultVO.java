package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 手动导出入参/出参（api-design.md §2.2：≤20 个 .md、单文件 ≤10MB；一文件一 doc）
 */
@Getter
@Setter
public class ImportDocsResultVO {

    private int submitted;

    private List<Accepted> accepted = new ArrayList<>();

    private List<Rejected> rejected = new ArrayList<>();

    @Getter
    @Setter
    public static class Accepted {
        private String originalName;
        private String docId;
        private String taskId;
    }

    @Getter
    @Setter
    public static class Rejected {
        private String originalName;
        /** TYPE_NOT_ALLOWED | TOO_LARGE | EMPTY_CONTENT */
        private String reason;
        private int code;
    }
}
