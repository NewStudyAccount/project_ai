package com.example.file.controller;

import com.example.file.common.Result;
import com.example.file.service.FileObjectService;
import com.example.file.vo.FileObjectVo;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文件对象 API：上传/删除/查询。访问 URL 公开读按 bucket；签名 URL 预留。 */
@RestController
@RequestMapping("/file/objects")
public class FileObjectController {

    private final FileObjectService fileObjectService;

    public FileObjectController(FileObjectService fileObjectService) {
        this.fileObjectService = fileObjectService;
    }

    /**
     * 上传。传 objectKey 则同 key 幂等覆盖。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileObjectVo> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "bizId", required = false) String bizId,
            @RequestParam(value = "scene", required = false) String scene,
            @RequestParam(value = "objectKey", required = false) String objectKey) {
        return Result.ok(fileObjectService.upload(file, bizType, bizId, scene, objectKey));
    }

    @GetMapping("/{id}")
    public Result<FileObjectVo> getById(@PathVariable("id") Long id) {
        return Result.ok(fileObjectService.getById(id));
    }

    @GetMapping
    public Result<List<FileObjectVo>> list(
            @RequestParam("bizType") String bizType,
            @RequestParam(value = "bizId", required = false) String bizId) {
        return Result.ok(fileObjectService.listByBiz(bizType, bizId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable("id") Long id) {
        fileObjectService.deleteById(id);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> deleteByKey(@RequestParam("objectKey") String objectKey) {
        fileObjectService.deleteByKey(objectKey);
        return Result.ok();
    }
}
