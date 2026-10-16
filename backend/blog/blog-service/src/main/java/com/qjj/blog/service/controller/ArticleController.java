package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.service.dto.ArticleContentRequest;
import com.qjj.blog.service.dto.ArticlePageQuery;
import com.qjj.blog.service.dto.ArticleRequest;
import com.qjj.blog.service.service.ArticleService;
import com.qjj.blog.service.vo.ArticleContentResultVO;
import com.qjj.blog.service.vo.ArticleDetailVO;
import com.qjj.blog.service.vo.ArticleVO;
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
 * 文章管理 API（主表/正文双轨/生命周期）。
 */
@RestController
@RequestMapping("/api/v1/admin/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public Result<PageResult<ArticleVO>> page(ArticlePageQuery query) {
        return Result.ok(articleService.pageAdmin(query));
    }

    @GetMapping("/{id}")
    public Result<ArticleDetailVO> detail(@PathVariable String id) {
        return Result.ok(articleService.detailAdmin(id));
    }

    @PostMapping
    public Result<ArticleDetailVO> create(@Valid @RequestBody ArticleRequest request) {
        return Result.ok(articleService.createMain(request));
    }

    @PutMapping("/{id}")
    public Result<ArticleVO> update(@PathVariable String id, @Valid @RequestBody ArticleRequest request) {
        return Result.ok(articleService.updateMain(id, request));
    }

    @PutMapping("/{id}/content")
    public Result<ArticleContentResultVO> saveContent(@PathVariable String id,
                                                      @Valid @RequestBody ArticleContentRequest request) {
        return Result.ok(articleService.saveContent(id, request));
    }

    @DeleteMapping("/{id}/draft")
    public Result<ArticleContentResultVO> discardDraft(@PathVariable String id) {
        return Result.ok(articleService.discardDraft(id));
    }

    @PostMapping("/{id}/publish")
    public Result<ArticleContentResultVO> publish(@PathVariable String id) {
        return Result.ok(articleService.publish(id));
    }

    @PostMapping("/{id}/offline")
    public Result<ArticleContentResultVO> offline(@PathVariable String id) {
        return Result.ok(articleService.offline(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        articleService.delete(id);
        return Result.ok();
    }
}
