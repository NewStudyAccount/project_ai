package com.qjj.blog.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.framework.core.IdGenerator;
import com.qjj.blog.knowledge.api.ArticleContentProvider;
import com.qjj.blog.knowledge.api.ArticleProjection;
import com.qjj.blog.knowledge.dto.CandidatePageQuery;
import com.qjj.blog.knowledge.dto.CandidateRow;
import com.qjj.blog.knowledge.entity.KnowledgeArticleIndex;
import com.qjj.blog.knowledge.entity.KnowledgeDoc;
import com.qjj.blog.knowledge.entity.KnowledgeTask;
import com.qjj.blog.knowledge.enums.IngestStateEnum;
import com.qjj.blog.knowledge.enums.KnowledgeDocSourceEnum;
import com.qjj.blog.knowledge.enums.KnowledgeDocStatusEnum;
import com.qjj.blog.knowledge.enums.KnowledgeTaskTypeEnum;
import com.qjj.blog.knowledge.mapper.KnowledgeArticleIndexMapper;
import com.qjj.blog.knowledge.mapper.KnowledgeDocMapper;
import com.qjj.blog.knowledge.service.ArticleIndexService;
import com.qjj.blog.knowledge.service.KnowledgeTaskService;
import com.qjj.blog.knowledge.vo.ArticleIndexSyncResultVO;
import com.qjj.blog.knowledge.vo.CandidateSummaryVO;
import com.qjj.blog.knowledge.vo.CandidateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章投影/候选服务实现（table-design.md §2.7）：
 * 同步 = 管理端手动动作（不落任务）；失效同步分支若涉及 doc 失效则落 knowledge_task(INVALIDATE) 异步清 ES。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleIndexServiceImpl implements ArticleIndexService, VectorizeService.ProjectionRefresher {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SYNC_PAGE_SIZE = 200;

    private final ArticleContentProvider articleContentProvider;
    private final KnowledgeArticleIndexMapper indexMapper;
    private final KnowledgeDocMapper docMapper;
    private final KnowledgeTaskService taskService;
    private final IdGenerator idGenerator;

    @Override
    @Transactional
    public ArticleIndexSyncResultVO sync() {
        ArticleIndexSyncResultVO result = new ArticleIndexSyncResultVO();

        // 1) 分页拉取已发布投影 → upsert（复用行语义）+ 统计
        Set<Long> pulledIds = new HashSet<>();
        List<KnowledgeArticleIndex> upserted = new ArrayList<>();
        int page = 1;
        while (true) {
            PageResult<ArticleProjection> pulled = articleContentProvider.pagePublished(page, SYNC_PAGE_SIZE);
            for (ArticleProjection p : pulled.getRecords()) {
                pulledIds.add(p.getArticleId());
                upserted.add(toIndex(p));
            }
            if (pulled.getRecords().size() < SYNC_PAGE_SIZE) {
                break;
            }
            page++;
        }
        result.setPulled(pulledIds.size());
        for (KnowledgeArticleIndex row : upserted) {
            KnowledgeArticleIndex existing = indexMapper.selectOne(new LambdaQueryWrapper<KnowledgeArticleIndex>()
                    .eq(KnowledgeArticleIndex::getArticleId, row.getArticleId())
                    .last("LIMIT 1"));
            if (existing == null) {
                row.setId(idGenerator.nextId());
                row.setSyncedAt(LocalDateTime.now());
                indexMapper.insert(row);
                result.setInserted(result.getInserted() + 1);
            } else {
                boolean changed = !row.getTitle().equals(existing.getTitle())
                        || !row.getCategoryId().equals(existing.getCategoryId())
                        || !row.getCategoryName().equals(existing.getCategoryName())
                        || !row.getPublishTime().equals(existing.getPublishTime())
                        || !row.getArticleUpdateTime().equals(existing.getArticleUpdateTime())
                        || !row.getLiveFileId().equals(existing.getLiveFileId())
                        || !row.getWordCount().equals(existing.getWordCount());
                existing.setTitle(row.getTitle());
                existing.setCategoryId(row.getCategoryId());
                existing.setCategoryName(row.getCategoryName());
                existing.setPublishTime(row.getPublishTime());
                existing.setArticleUpdateTime(row.getArticleUpdateTime());
                existing.setLiveFileId(row.getLiveFileId());
                existing.setWordCount(row.getWordCount());
                existing.setSyncedAt(LocalDateTime.now());
                indexMapper.updateById(existing);
                if (changed) {
                    result.setUpdated(result.getUpdated() + 1);
                }
            }
        }

        // 2) 全量覆盖：删除本次集合外的行（文章已下架/删除）
        List<Long> currentIds = indexMapper.listArticleIds();
        for (Long id : currentIds) {
            if (!pulledIds.contains(id)) {
                KnowledgeArticleIndex row = indexMapper.selectOne(new LambdaQueryWrapper<KnowledgeArticleIndex>()
                        .eq(KnowledgeArticleIndex::getArticleId, id)
                        .last("LIMIT 1"));
                if (row != null) {
                    indexMapper.deleteById(row.getId());
                    result.setRemoved(result.getRemoved() + 1);
                }
            }
        }

        // 3) 失效同步：doc(source=1) 的 source_id 不在投影表 → INVALID + ES doc_status=4（异步任务）
        List<KnowledgeDoc> articleDocs = docMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getSource, KnowledgeDocSourceEnum.ARTICLE.getCode())
                .ne(KnowledgeDoc::getStatus, KnowledgeDocStatusEnum.INVALID.getCode()));
        for (KnowledgeDoc doc : articleDocs) {
            if (!pulledIds.contains(doc.getSourceId())) {
                Long taskId = taskService.enqueue(KnowledgeTaskTypeEnum.INVALIDATE.getCode(),
                        doc.getId(), "{\"action\":\"INVALID\"}");
                if (taskId != null) {
                    result.setInvalidated(result.getInvalidated() + 1);
                }
            }
        }
        log.info("同步文章索引完成 pulled={} inserted={} updated={} removed={} invalidated={}",
                result.getPulled(), result.getInserted(), result.getUpdated(),
                result.getRemoved(), result.getInvalidated());
        return result;
    }

    @Override
    public PageResult<CandidateVO> candidates(CandidatePageQuery query) {
        long current = query.getCurrent() < 1 ? 1 : query.getCurrent();
        long size = query.getSize() < 1 ? 10 : Math.min(query.getSize(), 200);
        String orderBy = whitelistOrderBy(query.getOrderBy());
        String order = "asc".equalsIgnoreCase(query.getOrder()) ? "ASC" : "DESC";
        long offset = (current - 1) * size;
        List<CandidateRow> rows = indexMapper.pageCandidates(
                emptyToNull(query.getKeyword()), query.getCategoryId(),
                emptyToNull(query.getIngestState()), orderBy, order, offset, size);
        long total = indexMapper.countCandidates(
                emptyToNull(query.getKeyword()), query.getCategoryId(), emptyToNull(query.getIngestState()));
        List<CandidateVO> records = rows.stream().map(this::toVO).toList();
        return PageResult.of(records, total, size, current);
    }

    @Override
    public CandidateSummaryVO candidatesSummary() {
        Map<String, Object> raw = indexMapper.candidatesSummary();
        CandidateSummaryVO vo = new CandidateSummaryVO();
        vo.setTotal(asLong(raw.get("total")));
        vo.setUningested(asLong(raw.get("uningested")));
        vo.setIngested(asLong(raw.get("ingested")));
        vo.setStale(asLong(raw.get("stale")));
        vo.setInvalid(asLong(raw.get("invalid")));
        return vo;
    }

    @Override
    public void refresh(Long articleId, LocalDateTime articleUpdateTime) {
        indexMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<KnowledgeArticleIndex>()
                .eq(KnowledgeArticleIndex::getArticleId, articleId)
                .set(KnowledgeArticleIndex::getArticleUpdateTime, articleUpdateTime)
                .set(KnowledgeArticleIndex::getSyncedAt, LocalDateTime.now()));
    }

    private KnowledgeArticleIndex toIndex(ArticleProjection p) {
        KnowledgeArticleIndex row = new KnowledgeArticleIndex();
        row.setArticleId(p.getArticleId());
        row.setTitle(p.getTitle());
        row.setCategoryId(p.getCategoryId() == null ? 0L : p.getCategoryId());
        row.setCategoryName(p.getCategoryName() == null ? "" : p.getCategoryName());
        row.setPublishTime(p.getPublishTime());
        row.setArticleUpdateTime(p.getArticleUpdateTime());
        row.setLiveFileId(p.getLiveFileId() == null ? 0L : p.getLiveFileId());
        row.setWordCount(p.getWordCount() == null ? 0 : p.getWordCount());
        return row;
    }

    /** orderBy 白名单（api-design.md §1.1：publish_time 默认 | article_update_time） */
    private String whitelistOrderBy(String orderBy) {
        if ("article_update_time".equals(orderBy)) {
            return "i.article_update_time";
        }
        return "i.publish_time";
    }

    private CandidateVO toVO(CandidateRow row) {
        CandidateVO vo = new CandidateVO();
        vo.setArticleId(String.valueOf(row.getArticleId()));
        vo.setTitle(row.getTitle());
        vo.setCategoryId(String.valueOf(row.getCategoryId()));
        vo.setCategoryName(row.getCategoryName());
        vo.setPublishTime(format(row.getPublishTime()));
        vo.setUpdateTime(format(row.getArticleUpdateTime()));
        vo.setContentVersion(String.valueOf(row.getLiveFileId()));
        vo.setIngestState(row.getIngestState());
        vo.setDocId(row.getDocId() == null ? null : String.valueOf(row.getDocId()));
        vo.setDocStatus(row.getDocStatus());
        vo.setChunkCount(row.getChunkCount() == null ? 0 : row.getChunkCount());
        vo.setLastIngestTime(format(row.getLastIngestTime()));
        return vo;
    }

    private long asLong(Object v) {
        return v == null ? 0L : Long.parseLong(String.valueOf(v));
    }

    private String format(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        // MySQL DATETIME 字符串化即 yyyy-MM-dd HH:mm:ss
        return time.length() > 19 ? time.substring(0, 19) : time;
    }

    private String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
