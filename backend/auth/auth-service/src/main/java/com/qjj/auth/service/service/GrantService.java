package com.qjj.auth.service.service;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.service.dto.GrantQuery;
import com.qjj.auth.service.vo.GrantVO;

public interface GrantService {
    PageResult<GrantVO> page(GrantQuery query);
    void revoke(String id, String reason);
    void revokeByUser(String userId, String reason);
}
