package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.service.FileService;
import com.qjj.blog.service.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对内契约（仅服务间 Feign，网关不路由 /internal/**）。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final FileService fileService;

    @GetMapping("/files/{id}")
    public Result<FileVO> fileMeta(@PathVariable String id) {
        try {
            return Result.ok(fileService.get(id));
        } catch (Exception e) {
            return Result.ok(null);
        }
    }

    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of("pong", true, "ts", System.currentTimeMillis()));
    }
}
