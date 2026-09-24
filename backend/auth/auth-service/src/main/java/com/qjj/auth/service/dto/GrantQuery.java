package com.qjj.auth.service.dto;

import com.qjj.auth.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GrantQuery extends PageQuery {
    private Long userId;
    private String clientId;
    private Integer status;
}
