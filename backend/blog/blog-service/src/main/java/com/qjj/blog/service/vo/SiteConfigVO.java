package com.qjj.blog.service.vo;

import lombok.Data;

import java.util.Map;

@Data
public class SiteConfigVO {
    private String title;
    private String description;
    private String copyright;
    private String about;
    private Map<String, String> extra;
}
