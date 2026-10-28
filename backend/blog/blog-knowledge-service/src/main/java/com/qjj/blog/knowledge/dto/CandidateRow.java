package com.qjj.blog.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 候选文章行（knowledge_article_index LEFT JOIN knowledge_doc 查询结果）
 */
@Getter
@Setter
public class CandidateRow {

    private Long articleId;

    private String title;

    private Long categoryId;

    private String categoryName;

    private String publishTime;

    private String articleUpdateTime;

    private Long liveFileId;

    private Long docId;

    private Integer docStatus;

    private Integer chunkCount;

    private String lastIngestTime;

    private String ingestState;
}
