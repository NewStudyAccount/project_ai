package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.Result;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.service.dto.CategoryRequest;
import com.qjj.blog.service.service.CategoryService;
import com.qjj.blog.service.vo.CategoryVO;
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

import java.util.List;

/**
 * 分类管理 API。
 */
@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public Result<List<CategoryVO>> tree(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String name) {
        return Result.ok(categoryService.tree(status, name));
    }

    @GetMapping("/{id}")
    public Result<CategoryVO> get(@PathVariable String id) {
        return Result.ok(categoryService.get(id));
    }

    @PostMapping
    public Result<CategoryVO> create(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
