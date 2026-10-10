package com.qjj.blog.api.dto;

import lombok.Data;

import java.util.Map;

@Data
public class PingVO {
    private Boolean pong;
    private Long ts;
    private String degraded;
}
