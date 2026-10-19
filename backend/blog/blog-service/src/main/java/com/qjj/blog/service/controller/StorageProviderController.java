package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.dto.StorageProviderPageQuery;
import com.qjj.blog.service.dto.StorageProviderRequest;
import com.qjj.blog.service.dto.StorageProviderStatusRequest;
import com.qjj.blog.service.service.StorageProviderService;
import com.qjj.blog.service.vo.StorageProviderVO;
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
 * 存储服务商管理 API（object-storage-design.md §8.2）。
 * 权限码软校验/按钮显隐；网关鉴权；关键写 @Idempotent + 审计。
 */
@Tag(name = "存储服务商管理", description = "对象存储服务商配置 CRUD/连通性测试/启停/设默认")
@RestController
@RequestMapping("/api/v1/storage/providers")
@RequiredArgsConstructor
public class StorageProviderController {

    private final StorageProviderService providerService;

    @Operation(summary = "分页列表（SK 掩码）")
    @GetMapping
    public Result<PageResult<StorageProviderVO>> page(StorageProviderPageQuery query) {
        return Result.ok(providerService.page(query));
    }

    @Operation(summary = "详情（SK 掩码）")
    @GetMapping("/{id}")
    public Result<StorageProviderVO> get(@PathVariable String id) {
        return Result.ok(providerService.get(id));
    }

    @Operation(summary = "新建服务商（SK 加密落库）")
    @PostMapping
    public Result<StorageProviderVO> create(@Valid @RequestBody StorageProviderRequest request) {
        return Result.ok(providerService.create(request));
    }

    @Operation(summary = "更新服务商；secretKey 空=不修改")
    @PutMapping("/{id}")
    public Result<StorageProviderVO> update(@PathVariable String id,
                                            @Valid @RequestBody StorageProviderRequest request) {
        return Result.ok(providerService.update(id, request));
    }

    @Operation(summary = "连通性测试（headBucket 等价探测；失败可读原因，无 SK）")
    @PostMapping("/{id}/test")
    public Result<Void> test(@PathVariable String id) {
        providerService.test(id);
        return Result.ok();
    }

    @Operation(summary = "设为默认（只影响新上传，不批量改写历史 URL）")
    @PostMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable String id) {
        providerService.setDefault(id);
        return Result.ok();
    }

    @Operation(summary = "启用/停用（0 停用 / 1 启用）")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id,
                                     @Valid @RequestBody StorageProviderStatusRequest request) {
        providerService.updateStatus(id, request.getStatus());
        return Result.ok();
    }

    @Operation(summary = "逻辑删除；无活跃文件引用时才允许")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        providerService.delete(id);
        return Result.ok();
    }
}