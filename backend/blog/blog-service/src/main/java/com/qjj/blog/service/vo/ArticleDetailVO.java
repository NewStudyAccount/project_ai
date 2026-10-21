package com.qjj.blog.service.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleVO {

    /**
     * 正文（分端语义，api-design §0 / §3.3 / §6.4）：
     * 公开详情 = 服务端渲染 HTML（只读注入）；管理端详情 = Markdown 源文（编辑回写）。
     */
    @Schema(description = "正文。公开详情=服务端渲染 HTML（转义+URL 消毒）；管理端详情=Markdown 源文（编辑回写）")
    private String content;

    private String contentFrom;
    private Integer wordCount;
    private String seoTitle;
    private String seoDescription;
    private String draftFileId;
    private String contentFileId;
}
