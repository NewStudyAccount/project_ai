package com.qjj.blog.service.vo;

import lombok.Data;

/**
 * 公开图片读取结果（正文 Markdown 插图）。
 */
@Data
public class PublicImageVO {

    private String contentType;

    private String originalName;

    private byte[] content;
}
