package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 候选文章出参（api-design.md §1.1）
 */
@Getter
@Setter
public class CandidateVO {

    private String articleId;

    private String title;

    private String categoryId;

    private String categoryName;

    private String publishTime;

    private String updateTime;

    /** 当前 live 正文 sys_file.id（内容指纹；"0"=无正文，前端禁选） */
    private String contentVersion;

    private String ingestState;

    private String docId;

    private Integer docStatus;

    private Integer chunkCount;

    private String lastIngestTime;
}
