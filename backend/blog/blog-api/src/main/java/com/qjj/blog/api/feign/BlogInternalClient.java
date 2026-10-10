package com.qjj.blog.api.feign;

import com.qjj.blog.api.dto.FileMetaVO;
import com.qjj.blog.api.dto.PingVO;
import com.qjj.blog.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * blog 对内契约（仅服务间调用，不对公网暴露）。
 */
@FeignClient(name = "blog-service", path = "/internal", fallbackFactory = BlogInternalClientFallback.class)
public interface BlogInternalClient {

    @GetMapping("/files/{id}")
    Result<FileMetaVO> fileMeta(@PathVariable("id") String id);

    @GetMapping("/ping")
    Result<PingVO> ping();
}
