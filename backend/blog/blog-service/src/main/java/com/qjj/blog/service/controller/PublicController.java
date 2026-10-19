package com.qjj.blog.service.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.framework.idempotent.RateLimit;
import com.qjj.blog.service.dto.ArticlePageQuery;
import com.qjj.blog.service.service.ArticleService;
import com.qjj.blog.service.service.CategoryService;
import com.qjj.blog.service.service.FileService;
import com.qjj.blog.service.service.SiteConfigService;
import com.qjj.blog.service.service.TagService;
import com.qjj.blog.service.vo.ArticleDetailVO;
import com.qjj.blog.service.vo.ArticleVO;
import com.qjj.blog.service.vo.CategoryVO;
import com.qjj.blog.service.vo.SiteConfigVO;
import com.qjj.blog.service.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户端公开读 API（网关放行 /api/v1/public/**，无登录）。
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final CategoryService categoryService;
    private final TagService tagService;
    private final ArticleService articleService;
    private final SiteConfigService siteConfigService;
    private final FileService fileService;

    @GetMapping("/categories")
    @RateLimit(keyPrefix = "public-categories", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<List<CategoryVO>> categories() {
        return Result.ok(categoryService.tree(1, null));
    }

    @GetMapping("/tags")
    @RateLimit(keyPrefix = "public-tags", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<List<TagVO>> tags() {
        return Result.ok(tagService.listEnabled());
    }

    @GetMapping("/articles")
    @RateLimit(keyPrefix = "public-articles", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<PageResult<ArticleVO>> articles(ArticlePageQuery query) {
        return Result.ok(articleService.pagePublic(query));
    }

    @GetMapping("/articles/{idOrSlug}")
    @RateLimit(keyPrefix = "public-article-detail", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<ArticleDetailVO> articleDetail(@PathVariable String idOrSlug) {
        return Result.ok(articleService.detailPublic(idOrSlug));
    }

    @GetMapping("/articles/{idOrSlug}/content-url")
    @RateLimit(keyPrefix = "public-content-url", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<Map<String, String>> contentUrl(@PathVariable String idOrSlug) {
        return Result.ok(articleService.contentUrlPublic(idOrSlug));
    }

    @GetMapping("/site-config")
    @RateLimit(keyPrefix = "public-site-config", permits = 60, windowSeconds = 60, dimension = RateLimit.Dimension.IP)
    public Result<SiteConfigVO> siteConfig() {
        return Result.ok(siteConfigService.get());
    }

    /**
     * 正文插图兼容地址：302 跳转稳定公开 URL，不经业务中转对象 body。
     * 建议正文/Markdown 直接写入公开 `url`（object-storage-design §4.2）。
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<Void> publicFile(@PathVariable String id) {
        String url = fileService.publicImageUrl(id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }
}
