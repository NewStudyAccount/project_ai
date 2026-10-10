package com.qjj.blog.service.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.PresignVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileVO upload(MultipartFile file);

    PageResult<FileVO> page(FilePageQuery query);

    FileVO get(String id);

    PresignVO presign(String id);

    void logicalDelete(String id);
}
