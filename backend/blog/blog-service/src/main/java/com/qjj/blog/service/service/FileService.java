package com.qjj.blog.service.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.FileUrlVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileVO upload(MultipartFile file);

    /** 上传并登记业务关联（bizType 缺省 FILE，bizId 缺省 0） */
    FileVO upload(MultipartFile file, String bizType, Long bizId);

    /**
     * 上传并显式指定目标服务商（storageCode 为空则走默认链路）。
     * storageCode 不存在/停用 → 业务错误。
     */
    FileVO upload(MultipartFile file, String bizType, Long bizId, String storageCode);

    PageResult<FileVO> page(FilePageQuery query);

    FileVO get(String id);

    FileUrlVO getFileUrl(String id);

    /**
     * 公开读图片字节（正文 Markdown 插图用稳定 URL `/api/v1/public/files/{id}`）。
     * 仅允许 image/*；非图片或不存在 → 业务错误。
     */
    /** 公开插图稳定 URL（image/*）；兼容路径可 302 跳转 */
    String publicImageUrl(String id);

    void logicalDelete(String id);
}
