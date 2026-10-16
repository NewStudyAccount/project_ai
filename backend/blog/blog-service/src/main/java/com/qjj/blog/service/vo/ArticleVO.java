package com.qjj.blog.service.vo;

import lombok.Data;

import java.util.List;

@Data
public class ArticleVO {
    private String id;
    private String title;
    private String slug;
    private String summary;
    private String coverFileId;
    private String categoryId;
    private String categoryName;
    private List<TagVO> tags;
    private String authorId;
    private String authorName;
    private Integer status;
    private String publishTime;
    private Integer isTop;
    private Boolean hasDraft;
    private String createTime;
    private String updateTime;
}
