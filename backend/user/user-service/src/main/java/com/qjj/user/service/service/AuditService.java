package com.qjj.user.service.service;

import com.qjj.user.common.result.PageResult;
import com.qjj.user.service.dto.AuditPageQuery;
import com.qjj.user.service.enums.AuditActionEnum;
import com.qjj.user.service.vo.AuditVO;

public interface AuditService {

    void record(AuditActionEnum action, Long targetUserId, String detail);

    PageResult<AuditVO> page(AuditPageQuery query);
}
