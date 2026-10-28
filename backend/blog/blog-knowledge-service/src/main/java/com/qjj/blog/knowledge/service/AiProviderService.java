package com.qjj.blog.knowledge.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.knowledge.dto.AiProviderPageQuery;
import com.qjj.blog.knowledge.dto.AiProviderRequest;
import com.qjj.blog.knowledge.vo.AiProviderTestResultVO;
import com.qjj.blog.knowledge.vo.AiProviderVO;

/**
 * AI 服务商管理（api-design.md §3：CRUD + 连通测试；错误码 203110–203114）
 */
public interface AiProviderService {

    PageResult<AiProviderVO> page(AiProviderPageQuery query);

    AiProviderVO create(AiProviderRequest request);

    AiProviderVO update(String id, AiProviderRequest request);

    void delete(String id);

    AiProviderTestResultVO test(String id);
}
