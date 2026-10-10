package com.qjj.blog.api.dto;

import lombok.Data;

@Data
public class FileMetaVO {
    private String id;
    private String bucket;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
}
