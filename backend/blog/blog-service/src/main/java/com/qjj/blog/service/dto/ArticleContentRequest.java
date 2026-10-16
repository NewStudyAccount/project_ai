package com.qjj.blog.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存正文入参（双轨：mode=draft|publish）。
 */
@Data
public class ArticleContentRequest {

    @NotBlank(message = "mode 不能为空")
    private String mode;

    @NotBlank(message = "正文不能为空")
    @Size(max = 512000, message = "正文最长 500KB")
    private String content;

    private String contentFormat;

    @Size(max = 200, message = "SEO 标题最长 200")
    private String seoTitle;

    @Size(max = 300, message = "SEO 描述最长 300")
    private String seoDescription;
}
