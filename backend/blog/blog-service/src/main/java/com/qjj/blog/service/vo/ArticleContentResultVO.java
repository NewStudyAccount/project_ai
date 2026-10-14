package com.qjj.blog.service.vo;

import lombok.Data;

import java.util.Map;

/**
 * 保存正文/发布出参。
 */
@Data
public class ArticleContentResultVO {
    private String draftFileId;
    private String contentFileId;
    private Boolean hasDraft;
    private Integer status;
    private String publishTime;
}
