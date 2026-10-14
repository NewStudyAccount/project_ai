package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.core.UserContext;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.service.dto.ArticleContentRequest;
import com.qjj.blog.service.dto.ArticlePageQuery;
import com.qjj.blog.service.dto.ArticleRequest;
import com.qjj.blog.service.entity.BlogArticle;
import com.qjj.blog.service.entity.BlogArticleDetail;
import com.qjj.blog.service.entity.BlogArticleTag;
import com.qjj.blog.service.entity.BlogCategory;
import com.qjj.blog.service.entity.BlogTag;
import com.qjj.blog.service.enums.ArticleStatusEnum;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.enums.BizFileTypeEnum;
import com.qjj.blog.service.enums.ContentFormatEnum;
import com.qjj.blog.service.mapper.BlogArticleDetailMapper;
import com.qjj.blog.service.mapper.BlogArticleMapper;
import com.qjj.blog.service.mapper.BlogArticleTagMapper;
import com.qjj.blog.service.mapper.BlogCategoryMapper;
import com.qjj.blog.service.mapper.BlogTagMapper;
import com.qjj.blog.service.vo.ArticleContentResultVO;
import com.qjj.blog.service.vo.ArticleDetailVO;
import com.qjj.blog.service.vo.ArticleVO;
import com.qjj.blog.service.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章主表 + 正文双轨（草稿不掉线，发布才切换）。
 */
@Service
@RequiredArgsConstructor
public class ArticleService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ORDER_FIELDS = Set.of("publish_time", "is_top", "create_time");

    private final BlogArticleMapper articleMapper;
    private final BlogArticleDetailMapper detailMapper;
    private final BlogArticleTagMapper articleTagMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final ArticleContentStorageService contentStorage;

    public PageResult<ArticleVO> pageAdmin(ArticlePageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        Long categoryId = parseOptionalId(query.getCategoryId());
        Long tagId = parseOptionalId(query.getTagId());
        LambdaQueryWrapper<BlogArticle> wrapper = new LambdaQueryWrapper<BlogArticle>()
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        BlogArticle::getTitle, query.getKeyword())
                .eq(categoryId != null, BlogArticle::getCategoryId, categoryId)
                .eq(query.getStatus() != null, BlogArticle::getStatus, query.getStatus());
        if (tagId != null) {
            List<BlogArticleTag> refs = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                    .eq(BlogArticleTag::getTagId, tagId));
            if (refs.isEmpty()) {
                return PageResult.of(List.of(), 0, size, current);
            }
            List<Long> articleIds = refs.stream().map(BlogArticleTag::getArticleId).distinct().toList();
            wrapper.in(BlogArticle::getId, articleIds);
        }
        applyOrder(wrapper, query);
        Page<BlogArticle> page = articleMapper.selectPage(new Page<>(current, size), wrapper);
        List<ArticleVO> records = page.getRecords().stream()
                .map(a -> toVO(a, loadDetail(a.getId()), loadTags(a.getId()), loadCategoryName(a.getCategoryId())))
                .toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    public ArticleDetailVO detailAdmin(String id) {
        BlogArticle article = require(parseLong(id));
        BlogArticleDetail detail = loadDetail(article.getId());
        String content = "";
        String contentFrom = "none";
        String format = ContentFormatEnum.MARKDOWN.getCode();
        Integer wordCount = 0;
        String seoTitle = "";
        String seoDescription = "";
        if (detail != null && detail.getDraftFileId() != null && detail.getDraftFileId() > 0) {
            content = nullToEmpty(contentStorage.readContent(detail.getDraftFileId()));
            contentFrom = "draft";
            format = detail.getDraftContentFormat();
            wordCount = detail.getDraftWordCount();
            seoTitle = detail.getDraftSeoTitle();
            seoDescription = detail.getDraftSeoDescription();
        } else if (detail != null && detail.getContentFileId() != null && detail.getContentFileId() > 0) {
            content = nullToEmpty(contentStorage.readContent(detail.getContentFileId()));
            contentFrom = "live";
            format = detail.getContentFormat();
            wordCount = detail.getWordCount();
            seoTitle = detail.getSeoTitle();
            seoDescription = detail.getSeoDescription();
        }
        ArticleDetailVO vo = new ArticleDetailVO();
        copyArticle(article, vo, detail, loadTags(article.getId()), loadCategoryName(article.getCategoryId()));
        vo.setContent(content);
        vo.setContentFrom(contentFrom);
        vo.setContentFormat(format);
        vo.setWordCount(wordCount == null ? 0 : wordCount);
        vo.setSeoTitle(seoTitle == null ? "" : seoTitle);
        vo.setSeoDescription(seoDescription == null ? "" : seoDescription);
        if (detail != null) {
            vo.setDraftFileId(String.valueOf(detail.getDraftFileId() == null ? 0 : detail.getDraftFileId()));
            vo.setContentFileId(String.valueOf(detail.getContentFileId() == null ? 0 : detail.getContentFileId()));
        } else {
            vo.setDraftFileId("0");
            vo.setContentFileId("0");
        }
        return vo;
    }

    @Transactional
    @Idempotent(keyPrefix = "article-create")
    public ArticleDetailVO createMain(ArticleRequest request) {
        validateSlug(null, request.getSlug());
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()
                && !"0".equals(request.getCategoryId())) {
            if (categoryMapper.selectById(parseLong(request.getCategoryId())) == null) {
                throw new BizException(BlogErrorCodeEnum.CATEGORY_NOT_FOUND);
            }
        }
        BlogArticle article = new BlogArticle();
        article.setId(idGenerator.nextId());
        applyMain(article, request);
        article.setStatus(ArticleStatusEnum.DRAFT.getCode());
        article.setAuthorId(UserContext.userIdOrSystem());
        UserContext ctx = UserContext.get();
        article.setAuthorName(ctx == null || ctx.getUserName() == null ? "" : ctx.getUserName());
        article.setViewCount(0L);
        articleMapper.insert(article);
        BlogArticleDetail detail = new BlogArticleDetail();
        detail.setId(idGenerator.nextId());
        detail.setArticleId(article.getId());
        detail.setContentFileId(0L);
        detail.setDraftFileId(0L);
        detail.setContentFormat(ContentFormatEnum.MARKDOWN.getCode());
        detail.setWordCount(0);
        detail.setDraftContentFormat(ContentFormatEnum.MARKDOWN.getCode());
        detail.setDraftWordCount(0);
        detail.setSeoTitle("");
        detail.setSeoDescription("");
        detail.setDraftSeoTitle("");
        detail.setDraftSeoDescription("");
        detailMapper.insert(detail);
        replaceTags(article.getId(), request.getTagIds());
        auditService.record(AuditActionEnum.ARTICLE_CREATE, "ARTICLE", String.valueOf(article.getId()), article.getTitle());
        return detailAdmin(String.valueOf(article.getId()));
    }

    @Transactional
    @Idempotent(keyPrefix = "article-update")
    public ArticleVO updateMain(String id, ArticleRequest request) {
        BlogArticle article = require(parseLong(id));
        validateSlug(article.getId(), request.getSlug());
        if (request.getCategoryId() != null && !request.getCategoryId().isBlank()
                && !"0".equals(request.getCategoryId())) {
            if (categoryMapper.selectById(parseLong(request.getCategoryId())) == null) {
                throw new BizException(BlogErrorCodeEnum.CATEGORY_NOT_FOUND);
            }
        }
        applyMain(article, request);
        articleMapper.updateById(article);
        replaceTags(article.getId(), request.getTagIds());
        auditService.record(AuditActionEnum.ARTICLE_UPDATE, "ARTICLE", String.valueOf(article.getId()), article.getTitle());
        return toVO(article, loadDetail(article.getId()), loadTags(article.getId()), loadCategoryName(article.getCategoryId()));
    }

    @Transactional
    @Idempotent(keyPrefix = "article-content")
    public ArticleContentResultVO saveContent(String id, ArticleContentRequest request) {
        BlogArticle article = require(parseLong(id));
        BlogArticleDetail detail = requireDetail(article.getId());
        String mode = request.getMode() == null ? "draft" : request.getMode().trim().toLowerCase();
        if (!"draft".equals(mode) && !"publish".equals(mode)) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_FORMAT_INVALID);
        }
        String format = request.getContentFormat() == null || request.getContentFormat().isBlank()
                ? ContentFormatEnum.MARKDOWN.getCode() : request.getContentFormat().trim();
        Long newDraftFileId = contentStorage.putContent(article.getId(), true, request.getContent(), format);
        Long oldDraft = detail.getDraftFileId();
        detail.setDraftFileId(newDraftFileId);
        detail.setDraftContentFormat(format);
        detail.setDraftWordCount(countWords(request.getContent()));
        detail.setDraftSeoTitle(request.getSeoTitle() == null ? "" : request.getSeoTitle());
        detail.setDraftSeoDescription(request.getSeoDescription() == null ? "" : request.getSeoDescription());
        detailMapper.updateById(detail);
        contentStorage.logicalDeleteFile(oldDraft);
        auditService.record(AuditActionEnum.ARTICLE_CONTENT_UPDATE, "ARTICLE", String.valueOf(article.getId()),
                "mode=" + mode);
        if ("publish".equals(mode)) {
            return publish(String.valueOf(article.getId()));
        }
        return contentResult(article, detail);
    }

    @Transactional
    @Idempotent(keyPrefix = "article-draft-discard")
    public ArticleContentResultVO discardDraft(String id) {
        BlogArticle article = require(parseLong(id));
        BlogArticleDetail detail = requireDetail(article.getId());
        if (detail.getDraftFileId() == null || detail.getDraftFileId() <= 0) {
            throw new BizException(BlogErrorCodeEnum.DRAFT_NOT_FOUND);
        }
        contentStorage.logicalDeleteFile(detail.getDraftFileId());
        detail.setDraftFileId(0L);
        detail.setDraftContentFormat(ContentFormatEnum.MARKDOWN.getCode());
        detail.setDraftWordCount(0);
        detail.setDraftSeoTitle("");
        detail.setDraftSeoDescription("");
        detailMapper.updateById(detail);
        auditService.record(AuditActionEnum.ARTICLE_DRAFT_DISCARD, "ARTICLE", String.valueOf(article.getId()), "");
        return contentResult(article, detail);
    }

    @Transactional
    @Idempotent(keyPrefix = "article-publish")
    public ArticleContentResultVO publish(String id) {
        BlogArticle article = require(parseLong(id));
        BlogArticleDetail detail = requireDetail(article.getId());
        boolean hasDraft = detail.getDraftFileId() != null && detail.getDraftFileId() > 0;
        boolean hasLive = detail.getContentFileId() != null && detail.getContentFileId() > 0;
        if (!hasDraft && !hasLive) {
            throw new BizException(BlogErrorCodeEnum.CONTENT_REQUIRED);
        }
        if (hasDraft) {
            String draftContent = contentStorage.readContent(detail.getDraftFileId());
            if (draftContent == null) {
                throw new BizException(BlogErrorCodeEnum.CONTENT_REQUIRED);
            }
            Long newLive = contentStorage.putContent(article.getId(), false, draftContent, detail.getDraftContentFormat());
            Long oldLive = detail.getContentFileId();
            detail.setContentFileId(newLive);
            detail.setContentFormat(detail.getDraftContentFormat());
            detail.setWordCount(detail.getDraftWordCount());
            detail.setSeoTitle(detail.getDraftSeoTitle());
            detail.setSeoDescription(detail.getDraftSeoDescription());
            contentStorage.logicalDeleteFile(detail.getDraftFileId());
            contentStorage.logicalDeleteFile(oldLive);
            detail.setDraftFileId(0L);
            detail.setDraftContentFormat(ContentFormatEnum.MARKDOWN.getCode());
            detail.setDraftWordCount(0);
            detail.setDraftSeoTitle("");
            detail.setDraftSeoDescription("");
            detailMapper.updateById(detail);
        }
        article.setStatus(ArticleStatusEnum.PUBLISHED.getCode());
        article.setPublishTime(LocalDateTime.now());
        articleMapper.updateById(article);
        auditService.record(AuditActionEnum.ARTICLE_PUBLISH, "ARTICLE", String.valueOf(article.getId()), article.getTitle());
        return contentResult(article, detail);
    }

    @Transactional
    @Idempotent(keyPrefix = "article-offline")
    public ArticleContentResultVO offline(String id) {
        BlogArticle article = require(parseLong(id));
        if (!ArticleStatusEnum.PUBLISHED.getCode().equals(article.getStatus())) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_NOT_FOUND);
        }
        article.setStatus(ArticleStatusEnum.OFFLINE.getCode());
        articleMapper.updateById(article);
        auditService.record(AuditActionEnum.ARTICLE_OFFLINE, "ARTICLE", String.valueOf(article.getId()), article.getTitle());
        return contentResult(article, loadDetail(article.getId()));
    }

    @Transactional
    @Idempotent(keyPrefix = "article-delete")
    public void delete(String id) {
        BlogArticle article = require(parseLong(id));
        if (ArticleStatusEnum.PUBLISHED.getCode().equals(article.getStatus())) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_PUBLISHED);
        }
        articleMapper.deleteById(article.getId());
        BlogArticleDetail detail = loadDetail(article.getId());
        if (detail != null) {
            contentStorage.logicalDeleteFile(detail.getContentFileId());
            contentStorage.logicalDeleteFile(detail.getDraftFileId());
            detailMapper.deleteById(detail.getId());
        }
        List<BlogArticleTag> tags = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, article.getId()));
        for (BlogArticleTag tag : tags) {
            articleTagMapper.deleteById(tag.getId());
        }
        if (article.getCoverFileId() != null && article.getCoverFileId() > 0) {
            contentStorage.logicalDeleteFile(article.getCoverFileId());
        }
        auditService.record(AuditActionEnum.ARTICLE_DELETE, "ARTICLE", String.valueOf(article.getId()), article.getTitle());
    }

    // ---------- 用户端 ----------

    public PageResult<ArticleVO> pagePublic(ArticlePageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 50);
        Long categoryId = parseOptionalId(query.getCategoryId());
        Long tagId = parseOptionalId(query.getTagId());
        LambdaQueryWrapper<BlogArticle> wrapper = new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, ArticleStatusEnum.PUBLISHED.getCode())
                .like(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        BlogArticle::getTitle, query.getKeyword())
                .eq(categoryId != null, BlogArticle::getCategoryId, categoryId);
        if (tagId != null) {
            List<BlogArticleTag> refs = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                    .eq(BlogArticleTag::getTagId, tagId));
            if (refs.isEmpty()) {
                return PageResult.of(List.of(), 0, size, current);
            }
            wrapper.in(BlogArticle::getId, refs.stream().map(BlogArticleTag::getArticleId).distinct().toList());
        }
        String orderBy = query.getOrderBy() == null ? "publish_time" : query.getOrderBy().toLowerCase();
        if (!ORDER_FIELDS.contains(orderBy)) {
            orderBy = "publish_time";
        }
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        if (!asc && "publish_time".equals(orderBy)) {
            wrapper.orderByDesc(BlogArticle::getIsTop).orderByDesc(BlogArticle::getPublishTime);
        } else if ("is_top".equals(orderBy)) {
            wrapper.orderBy(true, asc, BlogArticle::getIsTop);
        } else if ("create_time".equals(orderBy)) {
            wrapper.orderBy(true, asc, BlogArticle::getCreateTime);
        } else {
            wrapper.orderBy(true, asc, BlogArticle::getPublishTime);
        }
        Page<BlogArticle> page = articleMapper.selectPage(new Page<>(current, size), wrapper);
        List<ArticleVO> records = page.getRecords().stream()
                .map(a -> toPublicVO(a, loadDetail(a.getId()), loadTags(a.getId()), loadCategoryName(a.getCategoryId())))
                .toList();
        return PageResult.of(records, page.getTotal(), size, current);
    }

    public ArticleDetailVO detailPublic(String idOrSlug) {
        BlogArticle article = findPublished(idOrSlug);
        BlogArticleDetail detail = loadDetail(article.getId());
        String content = "";
        if (detail != null && detail.getContentFileId() != null && detail.getContentFileId() > 0) {
            content = nullToEmpty(contentStorage.readContent(detail.getContentFileId()));
        }
        ArticleDetailVO vo = new ArticleDetailVO();
        copyArticle(article, vo, detail, loadTags(article.getId()), loadCategoryName(article.getCategoryId()));
        vo.setContent(content);
        vo.setContentFrom(detail != null && detail.getContentFileId() != null && detail.getContentFileId() > 0 ? "live" : "none");
        vo.setContentFormat(detail == null ? ContentFormatEnum.MARKDOWN.getCode() : detail.getContentFormat());
        vo.setWordCount(detail == null || detail.getWordCount() == null ? 0 : detail.getWordCount());
        vo.setSeoTitle(detail == null || detail.getSeoTitle() == null || detail.getSeoTitle().isBlank()
                ? article.getTitle() : detail.getSeoTitle());
        vo.setSeoDescription(detail == null || detail.getSeoDescription() == null || detail.getSeoDescription().isBlank()
                ? article.getSummary() : detail.getSeoDescription());
        return vo;
    }

    public Map<String, String> contentUrlPublic(String idOrSlug) {
        BlogArticle article = findPublished(idOrSlug);
        BlogArticleDetail detail = loadDetail(article.getId());
        if (detail == null || detail.getContentFileId() == null || detail.getContentFileId() <= 0) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_NOT_FOUND);
        }
        String url = contentStorage.presignGet(detail.getContentFileId());
        return Map.of("url", url,
                "expiresAt", LocalDateTime.now().plusMinutes(10).format(TIME));
    }

    private BlogArticle findPublished(String idOrSlug) {
        BlogArticle article = null;
        try {
            Long id = Long.valueOf(idOrSlug);
            article = articleMapper.selectById(id);
        } catch (NumberFormatException ignored) {
            // 按 slug 查
        }
        if (article == null) {
            article = articleMapper.selectOne(new LambdaQueryWrapper<BlogArticle>()
                    .eq(BlogArticle::getSlug, idOrSlug)
                    .last("limit 1"));
        }
        if (article == null || !ArticleStatusEnum.PUBLISHED.getCode().equals(article.getStatus())) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    // ---------- helpers ----------

    private void applyOrder(LambdaQueryWrapper<BlogArticle> wrapper, ArticlePageQuery query) {
        String orderBy = query.getOrderBy() == null ? "publish_time" : query.getOrderBy().toLowerCase();
        if (!ORDER_FIELDS.contains(orderBy)) {
            orderBy = "publish_time";
        }
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        if ("is_top".equals(orderBy)) {
            wrapper.orderBy(true, asc, BlogArticle::getIsTop);
        } else if ("create_time".equals(orderBy)) {
            wrapper.orderBy(true, asc, BlogArticle::getCreateTime);
        } else {
            wrapper.orderBy(true, asc, BlogArticle::getPublishTime);
        }
    }

    private void applyMain(BlogArticle article, ArticleRequest request) {
        article.setTitle(request.getTitle().trim());
        article.setSlug(request.getSlug() == null ? "" : request.getSlug().trim());
        article.setCategoryId(request.getCategoryId() == null || request.getCategoryId().isBlank()
                ? 0L : parseLong(request.getCategoryId()));
        article.setSummary(request.getSummary() == null ? "" : request.getSummary());
        article.setCoverFileId(request.getCoverFileId() == null || request.getCoverFileId().isBlank()
                || "0".equals(request.getCoverFileId()) ? 0L : parseLong(request.getCoverFileId()));
        article.setIsTop(request.getIsTop() == null ? 0 : request.getIsTop());
    }

    private void validateSlug(Long selfId, String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getSlug, slug.trim())
                .ne(selfId != null, BlogArticle::getId, selfId));
        if (count != null && count > 0) {
            throw new BizException(BlogErrorCodeEnum.SLUG_DUPLICATE);
        }
    }

    private void replaceTags(Long articleId, List<String> tagIds) {
        List<BlogArticleTag> old = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, articleId));
        for (BlogArticleTag item : old) {
            articleTagMapper.deleteById(item.getId());
        }
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (String tagId : tagIds) {
            if (tagId == null || tagId.isBlank()) {
                continue;
            }
            Long tid = parseLong(tagId);
            if (tagMapper.selectById(tid) == null) {
                throw new BizException(BlogErrorCodeEnum.TAG_NOT_FOUND);
            }
            BlogArticleTag rel = new BlogArticleTag();
            rel.setId(idGenerator.nextId());
            rel.setArticleId(articleId);
            rel.setTagId(tid);
            articleTagMapper.insert(rel);
        }
    }

    private BlogArticle require(Long id) {
        BlogArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    private BlogArticleDetail requireDetail(Long articleId) {
        BlogArticleDetail detail = loadDetail(articleId);
        if (detail == null) {
            detail = new BlogArticleDetail();
            detail.setId(idGenerator.nextId());
            detail.setArticleId(articleId);
            detail.setContentFileId(0L);
            detail.setDraftFileId(0L);
            detail.setContentFormat(ContentFormatEnum.MARKDOWN.getCode());
            detail.setWordCount(0);
            detail.setDraftContentFormat(ContentFormatEnum.MARKDOWN.getCode());
            detail.setDraftWordCount(0);
            detail.setSeoTitle("");
            detail.setSeoDescription("");
            detail.setDraftSeoTitle("");
            detail.setDraftSeoDescription("");
            detailMapper.insert(detail);
        }
        return detail;
    }

    private BlogArticleDetail loadDetail(Long articleId) {
        return detailMapper.selectOne(new LambdaQueryWrapper<BlogArticleDetail>()
                .eq(BlogArticleDetail::getArticleId, articleId)
                .last("limit 1"));
    }

    private List<TagVO> loadTags(Long articleId) {
        List<BlogArticleTag> refs = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, articleId));
        if (refs.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> tagIds = refs.stream().map(BlogArticleTag::getTagId).toList();
        List<BlogTag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream().map(t -> {
            TagVO vo = new TagVO();
            vo.setId(String.valueOf(t.getId()));
            vo.setName(t.getName());
            vo.setSlug(t.getSlug());
            vo.setSort(t.getSort());
            vo.setStatus(t.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    private String loadCategoryName(Long categoryId) {
        if (categoryId == null || categoryId == 0) {
            return "未分类";
        }
        BlogCategory category = categoryMapper.selectById(categoryId);
        return category == null ? "" : category.getName();
    }

    private void copyArticle(BlogArticle article, ArticleVO vo, BlogArticleDetail detail,
                             List<TagVO> tags, String categoryName) {
        vo.setId(String.valueOf(article.getId()));
        vo.setTitle(article.getTitle());
        vo.setSlug(article.getSlug());
        vo.setSummary(article.getSummary());
        vo.setCoverFileId(String.valueOf(article.getCoverFileId() == null ? 0 : article.getCoverFileId()));
        vo.setCategoryId(String.valueOf(article.getCategoryId() == null ? 0 : article.getCategoryId()));
        vo.setCategoryName(categoryName);
        vo.setTags(tags);
        vo.setAuthorId(String.valueOf(article.getAuthorId() == null ? 0 : article.getAuthorId()));
        vo.setAuthorName(article.getAuthorName());
        vo.setStatus(article.getStatus());
        vo.setPublishTime(article.getPublishTime() == null ? null : article.getPublishTime().format(TIME));
        vo.setIsTop(article.getIsTop());
        vo.setHasDraft(detail != null && detail.getDraftFileId() != null && detail.getDraftFileId() > 0);
        if (article.getCreateTime() != null) {
            vo.setCreateTime(article.getCreateTime().format(TIME));
        }
        if (article.getUpdateTime() != null) {
            vo.setUpdateTime(article.getUpdateTime().format(TIME));
        }
    }

    private ArticleVO toVO(BlogArticle article, BlogArticleDetail detail, List<TagVO> tags, String categoryName) {
        ArticleVO vo = new ArticleVO();
        copyArticle(article, vo, detail, tags, categoryName);
        return vo;
    }

    private ArticleVO toPublicVO(BlogArticle article, BlogArticleDetail detail, List<TagVO> tags, String categoryName) {
        ArticleVO vo = new ArticleVO();
        copyArticle(article, vo, detail, tags, categoryName);
        vo.setStatus(null);
        vo.setHasDraft(null);
        return vo;
    }

    private ArticleContentResultVO contentResult(BlogArticle article, BlogArticleDetail detail) {
        ArticleContentResultVO vo = new ArticleContentResultVO();
        vo.setDraftFileId(String.valueOf(detail == null || detail.getDraftFileId() == null ? 0 : detail.getDraftFileId()));
        vo.setContentFileId(String.valueOf(detail == null || detail.getContentFileId() == null ? 0 : detail.getContentFileId()));
        vo.setHasDraft(detail != null && detail.getDraftFileId() != null && detail.getDraftFileId() > 0);
        vo.setStatus(article.getStatus());
        vo.setPublishTime(article.getPublishTime() == null ? null : article.getPublishTime().format(TIME));
        return vo;
    }

    /** 空/null/0 → null；非法数字抛业务错。禁止在 LambdaQueryWrapper 条件参数里直接调 parseLong（会急切求值） */
    private Long parseOptionalId(String value) {
        if (value == null || value.isBlank() || "0".equals(value.trim())) {
            return null;
        }
        return parseLong(value.trim());
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.ARTICLE_NOT_FOUND);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int countWords(String content) {
        if (content == null) {
            return 0;
        }
        return content.replaceAll("\\s+", "").length();
    }
}
