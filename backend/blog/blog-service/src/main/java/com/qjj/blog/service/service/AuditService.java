package com.qjj.blog.service.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.service.dto.AuditPageQuery;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.vo.AuditVO;

public interface AuditService {

    /** 关键写审计（targetType 如 MENU/ROLE/FILE，targetId 为业务 id） */
    void record(AuditActionEnum action, String targetType, String targetId, String detail);

    PageResult<AuditVO> page(AuditPageQuery query);
}
