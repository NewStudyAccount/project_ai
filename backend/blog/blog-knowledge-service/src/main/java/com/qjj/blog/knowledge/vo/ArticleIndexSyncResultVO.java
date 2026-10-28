package com.qjj.blog.knowledge.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 同步文章索引进出参（api-design.md §1.3）
 */
@Getter
@Setter
public class ArticleIndexSyncResultVO {

    /** 本次拉取的已发布文章数 */
    private long pulled;

    /** 新增投影行 */
    private long inserted;

    /** 更新投影行 */
    private long updated;

    /** 移出投影行（文章已下架/删除） */
    private long removed;

    /** 触发失效的知识文档数 */
    private long invalidated;
}
