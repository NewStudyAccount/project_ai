package com.qjj.blog.service.dto;

import com.qjj.blog.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StorageProviderPageQuery extends PageQuery {

    private String keyword;

    private String providerType;

    private Integer status;
}