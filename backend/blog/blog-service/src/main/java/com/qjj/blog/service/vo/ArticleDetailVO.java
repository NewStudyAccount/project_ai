package com.qjj.blog.service.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleVO {
    private String content;
    private String contentFrom;
    private String contentFormat;
    private Integer wordCount;
    private String seoTitle;
    private String seoDescription;
    private String draftFileId;
    private String contentFileId;
}
