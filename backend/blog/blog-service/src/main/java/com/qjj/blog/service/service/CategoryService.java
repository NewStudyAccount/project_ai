package com.qjj.blog.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.framework.idempotent.Idempotent;
import com.qjj.blog.service.dto.CategoryRequest;
import com.qjj.blog.service.entity.BlogArticle;
import com.qjj.blog.service.entity.BlogCategory;
import com.qjj.blog.service.enums.AuditActionEnum;
import com.qjj.blog.service.mapper.BlogArticleMapper;
import com.qjj.blog.service.mapper.BlogCategoryMapper;
import com.qjj.blog.service.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类管理（树 ≤2 级）。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final BlogCategoryMapper categoryMapper;
    private final BlogArticleMapper articleMapper;
    private final IdGenerator idGenerator;
    private final AuditService auditService;

    public List<CategoryVO> tree(Integer status, String name) {
        LambdaQueryWrapper<BlogCategory> wrapper = new LambdaQueryWrapper<BlogCategory>()
                .eq(status != null, BlogCategory::getStatus, status)
                .like(name != null && !name.isBlank(), BlogCategory::getName, name)
                .orderByAsc(BlogCategory::getSort)
                .orderByAsc(BlogCategory::getId);
        List<BlogCategory> list = categoryMapper.selectList(wrapper);
        return buildTree(list);
    }

    public CategoryVO get(String id) {
        return toVO(require(parseId(id)));
    }

    @Transactional
    @Idempotent(keyPrefix = "category-create")
    public CategoryVO create(CategoryRequest request) {
        validateNameAndSlug(null, request);
        BlogCategory entity = new BlogCategory();
        entity.setId(idGenerator.nextId());
        fill(entity, request);
        categoryMapper.insert(entity);
        auditService.record(AuditActionEnum.CATEGORY_CREATE, "CATEGORY", String.valueOf(entity.getId()), entity.getName());
        return toVO(entity);
    }

    @Transactional
    @Idempotent(keyPrefix = "category-update")
    public CategoryVO update(String id, CategoryRequest request) {
        BlogCategory entity = require(parseId(id));
        validateNameAndSlug(entity.getId(), request);
        Long newParent = parseParent(request.getParentId());
        if (!entity.getParentId().equals(newParent)) {
            assertValidParent(entity.getId(), newParent);
        }
        fill(entity, request);
        categoryMapper.updateById(entity);
        auditService.record(AuditActionEnum.CATEGORY_UPDATE, "CATEGORY", String.valueOf(entity.getId()), entity.getName());
        return toVO(entity);
    }

    @Transactional
    @Idempotent(keyPrefix = "category-delete")
    public void delete(String id) {
        BlogCategory entity = require(parseId(id));
        Long countChildren = categoryMapper.selectCount(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getParentId, entity.getId()));
        if (countChildren != null && countChildren > 0) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_IN_USE);
        }
        Long countArticles = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getCategoryId, entity.getId()));
        if (countArticles != null && countArticles > 0) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_IN_USE);
        }
        categoryMapper.deleteById(entity.getId());
        auditService.record(AuditActionEnum.CATEGORY_DELETE, "CATEGORY", String.valueOf(entity.getId()), entity.getName());
    }

    private void fill(BlogCategory entity, CategoryRequest request) {
        entity.setParentId(parseParent(request.getParentId()));
        entity.setName(request.getName().trim());
        entity.setSlug(request.getSlug().trim());
        entity.setDescription(request.getDescription() == null ? "" : request.getDescription());
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void validateNameAndSlug(Long selfId, CategoryRequest request) {
        Long parent = parseParent(request.getParentId());
        Long nameCount = categoryMapper.selectCount(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getParentId, parent)
                .eq(BlogCategory::getName, request.getName().trim())
                .ne(selfId != null, BlogCategory::getId, selfId));
        if (nameCount != null && nameCount > 0) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_NAME_DUPLICATE);
        }
        Long slugCount = categoryMapper.selectCount(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getSlug, request.getSlug().trim())
                .ne(selfId != null, BlogCategory::getId, selfId));
        if (slugCount != null && slugCount > 0) {
            throw new BizException(BlogErrorCodeEnum.SLUG_DUPLICATE);
        }
        assertValidParent(selfId, parent);
    }

    private void assertValidParent(Long selfId, Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        BlogCategory parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_NOT_FOUND);
        }
        if (selfId != null && selfId.equals(parent.getId())) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_DEPTH_EXCEEDED);
        }
        // parent 自身若已是子级（parent.parent_id != 0），则新节点为第 3 级
        if (parent.getParentId() != null && parent.getParentId() != 0) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_DEPTH_EXCEEDED);
        }
        if (selfId != null) {
            // 禁止挂到自己子孙下：parent 的 parent 若是 self（2 级内等价于 parent 即子孙）
            if (selfId.equals(parent.getParentId())) {
                throw new BizException(BlogErrorCodeEnum.CATEGORY_DEPTH_EXCEEDED);
            }
        }
    }

    private Long parseParent(String parentId) {
        if (parentId == null || parentId.isBlank() || "0".equals(parentId)) {
            return 0L;
        }
        return parseId(parentId);
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (Exception e) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_NOT_FOUND);
        }
    }

    private BlogCategory require(Long id) {
        BlogCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new BizException(BlogErrorCodeEnum.CATEGORY_NOT_FOUND);
        }
        return entity;
    }

    private List<CategoryVO> buildTree(List<BlogCategory> list) {
        Map<Long, CategoryVO> map = new HashMap<>();
        for (BlogCategory item : list) {
            map.put(item.getId(), toVO(item));
        }
        List<CategoryVO> roots = new ArrayList<>();
        for (BlogCategory item : list) {
            CategoryVO vo = map.get(item.getId());
            Long parentId = item.getParentId() == null ? 0L : item.getParentId();
            if (parentId == 0 || !map.containsKey(parentId)) {
                roots.add(vo);
            } else {
                map.get(parentId).getChildren().add(vo);
            }
        }
        return roots;
    }

    private CategoryVO toVO(BlogCategory entity) {
        CategoryVO vo = new CategoryVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setParentId(String.valueOf(entity.getParentId() == null ? 0 : entity.getParentId()));
        vo.setName(entity.getName());
        vo.setSlug(entity.getSlug());
        vo.setDescription(entity.getDescription());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setChildren(new ArrayList<>());
        return vo;
    }
}
