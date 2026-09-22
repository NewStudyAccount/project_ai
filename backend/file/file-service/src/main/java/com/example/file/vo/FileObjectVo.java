package com.example.file.vo;

import java.time.LocalDateTime;

/** 文件对象出参。Long 由全局 Jackson 转 String。 */
public record FileObjectVo(
        Long id,
        String objectKey,
        String bucket,
        String url,
        String contentType,
        Long size,
        Long ownerId,
        String bizType,
        String bizId,
        String scene,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
