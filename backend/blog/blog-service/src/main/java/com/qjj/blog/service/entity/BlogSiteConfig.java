package com.qjj.blog.service.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 站点配置（config_key 多行）。
 */
@Getter
@Setter
@TableName("blog_site_config")
public class BlogSiteConfig extends BaseEntity {

    private String configKey;

    private String configValue;

    private String remark;
}
