package com.qjj.blog.service.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 稳定公开访问地址（object-storage-design §4.2）：无签名、无 expireAt。
 */
@Getter
@Setter
public class FileUrlVO {
    private String url;
}
