package com.qjj.blog.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qjj.blog.knowledge.dto.CandidateRow;
import com.qjj.blog.knowledge.entity.KnowledgeArticleIndex;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 已发布文章投影 Mapper（knowledge_article_index；候选列表/统计 SQL 见 XML） */
public interface KnowledgeArticleIndexMapper extends BaseMapper<KnowledgeArticleIndex> {

    /**
     * 候选文章分页（投影表 LEFT JOIN doc；ingestState 服务端筛选，SQL 对齐 table-design.md §2.7）。
     * orderBy/order 已在服务端白名单校验后拼入；offset/size 服务端计算。
     */
    List<CandidateRow> pageCandidates(@Param("keyword") String keyword,
                                      @Param("categoryId") Long categoryId,
                                      @Param("ingestState") String ingestState,
                                      @Param("orderBy") String orderBy,
                                      @Param("order") String order,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    /** 候选计数（同筛选条件） */
    long countCandidates(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         @Param("ingestState") String ingestState);

    /** 候选统计（四态聚合，全量不受分页影响） */
    Map<String, Object> candidatesSummary();

    /** 投影表 article_id 集合（全量覆盖同步用） */
    List<Long> listArticleIds();
}
