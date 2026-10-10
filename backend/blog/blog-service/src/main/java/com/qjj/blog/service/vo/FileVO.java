package com.qjj.blog.service.vo;

import lombok.Data;

@Data
public class FileVO {
    private String id;
    private String bucket;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private Integer deleted;
    private String createTime;
}
