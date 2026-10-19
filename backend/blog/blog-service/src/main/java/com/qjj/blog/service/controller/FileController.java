package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.dto.FilePageQuery;
import com.qjj.blog.service.service.FileService;
import com.qjj.blog.service.vo.FileVO;
import com.qjj.blog.service.vo.FileUrlVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理 API（example-system-design.md §5.2）；权限码仅软校验/按钮显隐。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping
    public Result<FileVO> upload(@RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) String bizType,
                                 @RequestParam(required = false) Long bizId,
                                 @RequestParam(required = false) String storageCode) {
        return Result.ok(fileService.upload(file, bizType, bizId, storageCode));
    }

    @GetMapping
    public Result<PageResult<FileVO>> page(FilePageQuery query) {
        return Result.ok(fileService.page(query));
    }

    @GetMapping("/{id}")
    public Result<FileVO> get(@PathVariable String id) {
        return Result.ok(fileService.get(id));
    }

    @GetMapping("/{id}/url")
    public Result<FileUrlVO> fileUrl(@PathVariable String id) {
        return Result.ok(fileService.getFileUrl(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        fileService.logicalDelete(id);
        return Result.ok();
    }
}
