package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.service.dto.TagRequest;
import com.qjj.blog.service.entity.BlogArticleTag;
import com.qjj.blog.service.entity.BlogTag;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.mapper.BlogArticleTagMapper;
import com.qjj.blog.service.mapper.BlogTagMapper;
import com.qjj.blog.service.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 标签管理。
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final BlogTagMapper tagMapper;
    private final BlogArticleTagMapper articleTagMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;

    public PageResult<TagVO> page(Long current, Long size, String name, Integer status) {
        long cur = current == null || current < 1 ? 1 : current;
        long sz = size == null || size < 1 ? 10 : Math.min(size, 200);
        LambdaQueryWrapper<BlogTag> wrapper = new LambdaQueryWrapper<BlogTag>()
                .like(name != null && !name.isBlank(), BlogTag::getName, name)
                .eq(status != null, BlogTag::getStatus, status)
                .orderByAsc(BlogTag::getSort)
                .orderByAsc(BlogTag::getId);
        Page<BlogTag> page = tagMapper.selectPage(new Page<>(cur, sz), wrapper);
        List<TagVO> records = page.getRecords().stream().map(this::toVO).toList();
        fillCounts(records);
        return PageResult.of(records, page.getTotal(), sz, cur);
    }

    public List<TagVO> listEnabled() {
        List<BlogTag> list = tagMapper.selectList(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getStatus, 1)
                .orderByAsc(BlogTag::getSort)
                .orderByAsc(BlogTag::getId));
        List<TagVO> records = list.stream().map(this::toVO).toList();
        fillCounts(records);
        return records;
    }

    @Transactional
    @Idempotent(keyPrefix = "tag-create")
    public TagVO create(TagRequest request) {
        validate(null, request);
        BlogTag entity = new BlogTag();
        entity.setId(idGenerator.nextId());
        fill(entity, request);
        tagMapper.insert(entity);
        auditService.record(AuditActionEnum.TAG_CREATE, "TAG", String.valueOf(entity.getId()), entity.getName());
        return toVO(entity);
    }

    @Transactional
    @Idempotent(keyPrefix = "tag-update")
    public TagVO update(String id, TagRequest request) {
        BlogTag entity = require(parseId(id));
        validate(entity.getId(), request);
        fill(entity, request);
        tagMapper.updateById(entity);
        auditService.record(AuditActionEnum.TAG_UPDATE, "TAG", String.valueOf(entity.getId()), entity.getName());
        return toVO(entity);
    }

    @Transactional
    @Idempotent(keyPrefix = "tag-delete")
    public void delete(String id) {
        BlogTag entity = require(parseId(id));
        Long refs = articleTagMapper.selectCount(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getTagId, entity.getId()));
        if (refs != null && refs > 0) {
            throw new BizException(BlogErrorCodeEnum.TAG_IN_USE);
        }
        tagMapper.deleteById(entity.getId());
        auditService.record(AuditActionEnum.TAG_DELETE, "TAG", String.valueOf(entity.getId()), entity.getName());
    }

    private void validate(Long selfId, TagRequest request) {
        Long nameCount = tagMapper.selectCount(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getName, request.getName().trim())
                .ne(selfId != null, BlogTag::getId, selfId));
        if (nameCount != null && nameCount > 0) {
            throw new BizException(BlogErrorCodeEnum.TAG_NAME_DUPLICATE);
        }
        Long slugCount = tagMapper.selectCount(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getSlug, request.getSlug().trim())
                .ne(selfId != null, BlogTag::getId, selfId));
        if (slugCount != null && slugCount > 0) {
            throw new BizException(BlogErrorCodeEnum.SLUG_DUPLICATE);
        }
    }

    private void fill(BlogTag entity, TagRequest request) {
        entity.setName(request.getName().trim());
        entity.setSlug(request.getSlug().trim());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void fillCounts(List<TagVO> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(r -> Long.valueOf(r.getId())).toList();
        List<BlogArticleTag> refs = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .in(BlogArticleTag::getTagId, ids));
        Map<Long, Long> countMap = refs.stream()
                .collect(Collectors.groupingBy(BlogArticleTag::getTagId, Collectors.counting()));
        for (TagVO vo : records) {
            vo.setArticleCount(countMap.getOrDefault(Long.valueOf(vo.getId()), 0L));
        }
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.TAG_NOT_FOUND);
        }
    }

    private BlogTag require(Long id) {
        BlogTag entity = tagMapper.selectById(id);
        if (entity == null) {
            throw new BizException(BlogErrorCodeEnum.TAG_NOT_FOUND);
        }
        return entity;
    }

    private TagVO toVO(BlogTag entity) {
        TagVO vo = new TagVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setName(entity.getName());
        vo.setSlug(entity.getSlug());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setArticleCount(0L);
        return vo;
    }
}
