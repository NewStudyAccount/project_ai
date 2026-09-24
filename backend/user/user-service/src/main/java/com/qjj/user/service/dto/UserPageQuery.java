package com.qjj.user.service.dto;

import com.qjj.user.common.page.PageQuery;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserPageQuery extends PageQuery {

    private String username;

    private Integer status;

    private String orderBy = "createTime";

    private String order = "desc";
}
