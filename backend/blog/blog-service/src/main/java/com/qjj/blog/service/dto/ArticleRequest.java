package com.qjj.blog.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 文章主表入参（不含正文）。
 */
@Data
public class ArticleRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200")
    private String title;

    @Size(max = 120, message = "slug 最长 120")
    private String slug;

    private String categoryId;

    @Size(max = 500, message = "摘要最长 500")
    private String summary;

    private String coverFileId;

    private List<String> tagIds;

    private Integer isTop;
}
