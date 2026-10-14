package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.dto.TagRequest;
import com.qjj.blog.service.service.TagService;
import com.qjj.blog.service.vo.TagVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标签管理 API。
 */
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public Result<PageResult<TagVO>> page(@RequestParam(required = false) Long current,
                                          @RequestParam(required = false) Long size,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(tagService.page(current, size, name, status));
    }

    @PostMapping
    public Result<TagVO> create(@Valid @RequestBody TagRequest request) {
        return Result.ok(tagService.create(request));
    }

    @PutMapping("/{id}")
    public Result<TagVO> update(@PathVariable String id, @Valid @RequestBody TagRequest request) {
        return Result.ok(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        tagService.delete(id);
        return Result.ok();
    }
}
