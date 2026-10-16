package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.service.SiteConfigService;
import com.qjj.blog.service.vo.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站点配置管理 API。
 */
@RestController
@RequestMapping("/api/v1/admin/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigService siteConfigService;

    @GetMapping
    public Result<SiteConfigVO> get() {
        return Result.ok(siteConfigService.get());
    }

    @PutMapping
    public Result<SiteConfigVO> save(@RequestBody Map<String, String> body) {
        return Result.ok(siteConfigService.save(body));
    }
}
