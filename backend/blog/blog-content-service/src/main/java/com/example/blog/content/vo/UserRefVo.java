package com.example.blog.content.vo;

import java.time.LocalDateTime;

/** 用户投影出参。 */
public record UserRefVo(
        Long userId,
        String username,
        String realName,
        Integer status,
        LocalDateTime syncTime
) {
}
