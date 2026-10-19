package com.qjj.blog.service.dto;

import lombok.Data;

@Data
public class FilePageQuery {
    private Long current = 1L;
    private Long size = 10L;
    private String keyword;
    private String contentType;
    private String bizType;
    private String storageCode;
    private String orderBy;
    private String order;
}
