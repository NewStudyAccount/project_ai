package com.qjj.blog.knowledge.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.knowledge.dto.AiProviderPageQuery;
import com.qjj.blog.knowledge.dto.AiProviderRequest;
import com.qjj.blog.knowledge.service.AiProviderService;
import com.qjj.blog.knowledge.vo.AiProviderTestResultVO;
import com.qjj.blog.knowledge.vo.AiProviderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 服务商管理 API（api-design.md §3；权限位 blog:ai-provider:*，网关鉴权）。
 */
@Tag(name = "AI 服务商管理", description = "知识库 AI 服务商配置 CRUD/连通测试")
@RestController
@RequestMapping("/api/v1/admin/knowledge/ai-providers")
@RequiredArgsConstructor
public class AiProviderController {

    private final AiProviderService providerService;

    @Operation(summary = "分页列表（apiKey 掩码）")
    @GetMapping
    public Result<PageResult<AiProviderVO>> page(AiProviderPageQuery query) {
        return Result.ok(providerService.page(query));
    }

    @Operation(summary = "新建服务商（code 唯一；幂等）")
    @PostMapping
    public Result<AiProviderVO> create(@Valid @RequestBody AiProviderRequest request) {
        return Result.ok(providerService.create(request));
    }

    @Operation(summary = "更新服务商；apiKey 空=不修改")
    @PutMapping("/{id}")
    public Result<AiProviderVO> update(@PathVariable String id,
                                       @Valid @RequestBody AiProviderRequest request) {
        return Result.ok(providerService.update(id, request));
    }

    @Operation(summary = "删除服务商（生效默认行禁删 203113）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        providerService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "连通测试（失败 203112；出参 ok/latencyMs/message）")
    @PostMapping("/{id}/test")
    public Result<AiProviderTestResultVO> test(@PathVariable String id) {
        return Result.ok(providerService.test(id));
    }
}
