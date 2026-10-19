package com.qjj.blog.service.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.service.dto.StorageProviderPageQuery;
import com.qjj.blog.service.dto.StorageProviderRequest;
import com.qjj.blog.service.vo.StorageProviderVO;

public interface StorageProviderService {

    PageResult<StorageProviderVO> page(StorageProviderPageQuery query);

    StorageProviderVO get(String id);

    StorageProviderVO create(StorageProviderRequest request);

    StorageProviderVO update(String id, StorageProviderRequest request);

    /** 连通性测试；失败返回业务错误码 + 可读原因，成功记录审计 */
    void test(String id);

    /** 设为默认服务商；只影响新上传，不批量改写历史 URL */
    void setDefault(String id);

    /** 启用/停用 */
    void updateStatus(String id, Integer status);

    /** 逻辑删除；无活跃文件引用时才允许 */
    void delete(String id);
}