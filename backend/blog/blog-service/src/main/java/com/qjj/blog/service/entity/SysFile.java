package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 上传文件元数据（object-storage-design.md §5；删除语义 = deleted）。
 */
@Getter
@Setter
@TableName("sys_file")
public class SysFile extends BaseEntity {

    private String bucket;

    private String objectKey;

    private String originalName;

    private String contentType;

    private Long sizeBytes;
}
