package com.qjj.auth.service.service;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.service.dto.AuditQuery;
import com.qjj.auth.service.dto.LoginAttemptQuery;
import com.qjj.auth.service.vo.AuditVO;

public interface AuditService {
    void record(String action, String targetType, String targetId, String detail);
    PageResult<AuditVO> page(AuditQuery query);
    PageResult<AuditVO> loginAttempts(LoginAttemptQuery query);
}
