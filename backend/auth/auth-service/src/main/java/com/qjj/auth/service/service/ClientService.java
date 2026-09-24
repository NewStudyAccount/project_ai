package com.qjj.auth.service.service;

import com.qjj.auth.common.result.PageResult;
import com.qjj.auth.service.dto.ClientRequest;
import com.qjj.auth.service.vo.ClientSecretVO;
import com.qjj.auth.service.vo.ClientVO;

public interface ClientService {
    PageResult<ClientVO> page(long current, long size, String clientId, String systemCode, Integer enabled);
    ClientSecretVO create(ClientRequest request);
    ClientVO update(String id, ClientRequest request);
    ClientVO updateStatus(String id, Integer enabled);
    ClientSecretVO resetSecret(String id);
    void delete(String id);
}
