package com.qjj.blog.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.service.entity.BlogArticle;
import com.qjj.blog.service.entity.BlogArticleDetail;
import com.qjj.blog.service.mapper.BlogArticleDetailMapper;
import com.qjj.blog.service.mapper.BlogArticleMapper;
import com.qjj.blog.service.service.FileReferenceChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章侧文件引用：封面 / 线上正文 / 草稿（table-design §2.0）。
 */
@Component
@RequiredArgsConstructor
public class ArticleFileReferenceChecker implements FileReferenceChecker {

    private final BlogArticleMapper articleMapper;
    private final BlogArticleDetailMapper detailMapper;

    @Override
    public FileReferenceResult check(Long fileId) {
        if (fileId == null) {
            return FileReferenceResult.none();
        }
        List<String> refs = new ArrayList<>();

        Long coverCount = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getCoverFileId, fileId));
        if (coverCount != null && coverCount > 0) {
            refs.add("文章封面占用");
        }

        Long contentCount = detailMapper.selectCount(new LambdaQueryWrapper<BlogArticleDetail>()
                .eq(BlogArticleDetail::getContentFileId, fileId));
        if (contentCount != null && contentCount > 0) {
            refs.add("文章正文占用");
        }

        Long draftCount = detailMapper.selectCount(new LambdaQueryWrapper<BlogArticleDetail>()
                .eq(BlogArticleDetail::getDraftFileId, fileId));
        if (draftCount != null && draftCount > 0) {
            refs.add("文章草稿占用");
        }

        return FileReferenceResult.of(refs);
    }
}
