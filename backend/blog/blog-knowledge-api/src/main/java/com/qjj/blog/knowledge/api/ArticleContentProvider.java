package com.qjj.blog.knowledge.api;

import com.qjj.blog.common.result.PageResult;

/**
 * blog 文章只读契约（blog-knowledge-base 提案 tasks 5.1 / table-design.md 裁决 12）：
 * <ul>
 *   <li>blog-service 仅提供<b>唯一一个只读实现</b>：不改任何既有业务类、不发事件；</li>
 *   <li>① 分页拉取已发布文章投影（status=1，publish_time 倒序）；</li>
 *   <li>② 按 id 回拉正文 + 标题/分类/标签元数据。</li>
 * </ul>
 * 知识库侧（blog-knowledge-service）经本接口消费，blog 侧一行业务逻辑不动。
 */
public interface ArticleContentProvider {

    /**
     * 分页拉取已发布文章投影（status=1）。
     *
     * @param current 页码（1 起）
     * @param size    页大小（投影同步固定 200 循环拉取）
     * @return publish_time 倒序分页
     */
    PageResult<ArticleProjection> pagePublished(int current, int size);

    /**
     * 按 id 回拉正文与元数据。
     *
     * @param articleId 文章 id
     * @return 不存在/未发布/正文缺失时返回 null（调用方按 NOT_PUBLISHED / NO_CONTENT 区分需自行查投影）
     */
    ArticleContent fetchArticle(Long articleId);
}
