package com.qjj.blog.service.vo;

import lombok.Data;

@Data
public class PresignVO {
    private String url;
    private String expireAt;
}
