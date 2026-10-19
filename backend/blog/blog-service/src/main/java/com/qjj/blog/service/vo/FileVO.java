package com.qjj.blog.service.vo;

import lombok.Data;

@Data
public class FileVO {
    private String id;
    private String storageCode;
    private String bucket;
    private String objectKey;
    /** 稳定公开 URL（publicBaseUrl + objectKey） */
    private String url;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String bizType;
    private Long bizId;
    private Integer deleted;
    private String createTime;
}
