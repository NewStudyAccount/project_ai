package com.qjj.blog.service.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.knowledge.api.ArticleContent;
import com.qjj.blog.knowledge.api.ArticleContentProvider;
import com.qjj.blog.knowledge.api.ArticleProjection;
import com.qjj.blog.service.entity.BlogArticle;
import com.qjj.blog.service.entity.BlogArticleDetail;
import com.qjj.blog.service.entity.BlogArticleTag;
import com.qjj.blog.service.entity.BlogCategory;
import com.qjj.blog.service.entity.BlogTag;
import com.qjj.blog.service.mapper.BlogArticleDetailMapper;
import com.qjj.blog.service.mapper.BlogArticleMapper;
import com.qjj.blog.service.mapper.BlogArticleTagMapper;
import com.qjj.blog.service.mapper.BlogCategoryMapper;
import com.qjj.blog.service.mapper.BlogTagMapper;
import com.qjj.blog.service.service.ArticleContentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * blog 文章只读实现（blog-knowledge-base 提案 tasks 5.2 / 裁决 12）：
 * 本类是 blog-service 为知识库提供的<b>唯一</b>新增文件——不改任何既有业务类、不发事件；
 * 只读 blog_article / blog_article_detail / blog_article_tag / blog_category / blog_tag，
 * 正文经既有 {@link ArticleContentStorageService#readContent} 回源。
 */
@Component
@RequiredArgsConstructor
public class ArticleContentProviderImpl implements ArticleContentProvider {

    /** 已发布 */
    private static final int ARTICLE_STATUS_PUBLISHED = 1;

    private final BlogArticleMapper articleMapper;
    private final BlogArticleDetailMapper articleDetailMapper;
    private final BlogArticleTagMapper articleTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final ArticleContentStorageService contentStorageService;

    @Override
    public PageResult<ArticleProjection> pagePublished(int current, int size) {
        Page<BlogArticle> page = articleMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<BlogArticle>()
                        .eq(BlogArticle::getStatus, ARTICLE_STATUS_PUBLISHED)
                        .orderByDesc(BlogArticle::getPublishTime)
                        .orderByDesc(BlogArticle::getId));
        List<ArticleProjection> records = page.getRecords().stream().map(this::toProjection).toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    @Override
    public ArticleContent fetchArticle(Long articleId) {
        BlogArticle article = articleMapper.selectById(articleId);
        if (article == null || Integer.valueOf(1).equals(article.getDeleted())
                || article.getStatus() == null || article.getStatus() != ARTICLE_STATUS_PUBLISHED) {
            return null;
        }
        BlogArticleDetail detail = articleDetailMapper.selectOne(new LambdaQueryWrapper<BlogArticleDetail>()
                .eq(BlogArticleDetail::getArticleId, articleId)
                .last("LIMIT 1"));
        long liveFileId = detail == null || detail.getContentFileId() == null ? 0L : detail.getContentFileId();
        ArticleContent content = new ArticleContent();
        content.setArticleId(article.getId());
        content.setTitle(article.getTitle());
        content.setSlug(article.getSlug());
        content.setCategoryId(article.getCategoryId() == null ? 0L : article.getCategoryId());
        content.setCategoryName(categoryName(article.getCategoryId()));
        content.setTags(tagNames(articleId));
        content.setPublishTime(article.getPublishTime());
        content.setArticleUpdateTime(article.getUpdateTime());
        content.setLiveFileId(liveFileId);
        content.setWordCount(detail == null ? 0 : (detail.getWordCount() == null ? 0 : detail.getWordCount()));
        content.setContent(liveFileId > 0 ? contentStorageService.readContent(liveFileId) : null);
        return content;
    }

    private ArticleProjection toProjection(BlogArticle article) {
        BlogArticleDetail detail = articleDetailMapper.selectOne(new LambdaQueryWrapper<BlogArticleDetail>()
                .eq(BlogArticleDetail::getArticleId, article.getId())
                .last("LIMIT 1"));
        ArticleProjection p = new ArticleProjection();
        p.setArticleId(article.getId());
        p.setTitle(article.getTitle());
        p.setCategoryId(article.getCategoryId() == null ? 0L : article.getCategoryId());
        p.setCategoryName(categoryName(article.getCategoryId()));
        p.setPublishTime(article.getPublishTime());
        p.setArticleUpdateTime(article.getUpdateTime());
        p.setLiveFileId(detail == null || detail.getContentFileId() == null ? 0L : detail.getContentFileId());
        p.setWordCount(detail == null ? 0 : (detail.getWordCount() == null ? 0 : detail.getWordCount()));
        p.setSlug(article.getSlug());
        return p;
    }

    private String categoryName(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            return "";
        }
        BlogCategory category = categoryMapper.selectById(categoryId);
        return category == null || Integer.valueOf(1).equals(category.getDeleted()) ? "" : category.getName();
    }

    private List<String> tagNames(Long articleId) {
        List<BlogArticleTag> relations = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, articleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = relations.stream().map(BlogArticleTag::getTagId).filter(Objects::nonNull).toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> names = tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> !Integer.valueOf(1).equals(t.getDeleted()))
                .collect(Collectors.toMap(BlogTag::getId, BlogTag::getName));
        return relations.stream()
                .map(r -> names.get(r.getTagId()))
                .filter(Objects::nonNull)
                .toList();
    }
}
