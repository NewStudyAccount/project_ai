package com.example.file.controller;

import com.example.file.common.BizException;
import com.example.file.service.FileObjectService;
import com.example.file.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开内容读取（publicBaseUrl 指向本路径）。签名 URL 能力预留。
 */
@RestController
@RequestMapping("/file/objects/content")
public class FileContentController {

    private static final String PREFIX = "/file/objects/content/";

    private final ObjectStorage objectStorage;
    private final FileObjectService fileObjectService;

    public FileContentController(ObjectStorage objectStorage, FileObjectService fileObjectService) {
        this.objectStorage = objectStorage;
        this.fileObjectService = fileObjectService;
    }

    @GetMapping("/**")
    public void content(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String uri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String path = uri.substring(context.length());
        if (!path.startsWith(PREFIX) || path.length() <= PREFIX.length()) {
            throw BizException.badParam("objectKey 非法");
        }
        String objectKey = path.substring(PREFIX.length());
        var meta = fileObjectService.getByKey(objectKey);
        response.setContentType(meta.contentType() == null ? "application/octet-stream" : meta.contentType());
        if (meta.size() != null) {
            response.setContentLengthLong(meta.size());
        }
        try (InputStream in = objectStorage.get(objectKey); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
