package com.qjj.blog.service.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.PresignVO;
import com.qjj.blog.service.vo.PublicImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileVO upload(MultipartFile file);

    /** 上传并登记业务关联（bizType 缺省 FILE，bizId 缺省 0） */
    FileVO upload(MultipartFile file, String bizType, Long bizId);

    PageResult<FileVO> page(FilePageQuery query);

    FileVO get(String id);

    PresignVO presign(String id);

    /**
     * 公开读图片字节（正文 Markdown 插图用稳定 URL `/api/v1/public/files/{id}`）。
     * 仅允许 image/*；非图片或不存在 → 业务错误。
     */
    PublicImageVO readPublicImage(String id);

    void logicalDelete(String id);
}
