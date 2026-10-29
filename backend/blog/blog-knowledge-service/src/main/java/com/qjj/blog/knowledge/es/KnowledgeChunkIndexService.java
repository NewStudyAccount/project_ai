package com.qjj.blog.knowledge.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateByQueryRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.knowledge.config.KnowledgeElasticsearchProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 检索索引服务（knowledge_chunk_v1 唯一 index；table-design.md §3）。
 * <ul>
 *   <li>文档 _id = knowledge_chunk.id（字符串化），幂等覆盖写；</li>
 *   <li>检索：kNN(embedding) + match(content, ik_smart) + filter(doc_status=2) → ES 原生 RRF → Top-K，
 *       出处 denormalize（doc_title/doc_url），全链路不回 MySQL；</li>
 *   <li>ES 不可用 → BizException(203100)（ask 控制器映射 503；入库侧降级失败留痕重试）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkIndexService {

    /** index mapping/settings 契约（deploy/es/knowledge_chunk_v1.json 同源；FULL_REINDEX 重建用） */
    private static final String INDEX_DEFINITION = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "refresh_interval": "1s"
              },
              "mappings": {
                "properties": {
                  "chunk_id":   { "type": "keyword" },
                  "doc_id":     { "type": "long" },
                  "heading":    { "type": "text", "analyzer": "ik_smart" },
                  "content":    { "type": "text", "analyzer": "ik_smart" },
                  "embedding":  { "type": "dense_vector", "dims": 1024,
                                  "index": true, "similarity": "cosine",
                                  "index_options": { "type": "hnsw", "m": 16, "ef_construction": 100 } },
                  "source":     { "type": "byte" },
                  "tags":       { "type": "keyword" },
                  "doc_status": { "type": "byte" },
                  "doc_title":  { "type": "text", "analyzer": "ik_smart" },
                  "doc_url":    { "type": "keyword" }
                }
              }
            }""";

    private final ElasticsearchClient client;
    private final KnowledgeElasticsearchProperties props;

    @PostConstruct
    void validateIndexName() {
        if (!"knowledge_chunk_v1".equals(props.getIndexName())) {
            log.warn("knowledge ES index 名已覆盖为 {}（契约仅 knowledge_chunk_v1，确认非误配）", props.getIndexName());
        }
    }

    /** ES 是否可用（集群健康 + index 存在） */
    public boolean healthy() {
        try {
            client.cluster().health(new HealthRequest.Builder().build());
            BooleanResponse exists = client.indices().exists(e -> e.index(props.getIndexName()));
            return exists.value();
        } catch (Exception e) {
            log.warn("ES health check failed: {}", e.getMessage());
            return false;
        }
    }

    /** index 文档数；ES 不可用返回 null（统计容错） */
    public Long countOrNull() {
        try {
            return client.count(new CountRequest.Builder().index(props.getIndexName()).build()).count();
        } catch (Exception e) {
            log.warn("ES count failed: {}", e.getMessage());
            return null;
        }
    }

    /** 批量 index（_id=chunkId 幂等覆盖） */
    public void bulkIndex(List<EsChunkDoc> docs) {
        try {
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (EsChunkDoc doc : docs) {
                bulk.operations(op -> op.index(idx -> idx
                        .index(props.getIndexName())
                        .id(doc.chunkId())
                        .document(toMap(doc))));
            }
            BulkResponse resp = client.bulk(bulk.build());
            if (resp.errors()) {
                String first = resp.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.id() + ":" + i.error().reason())
                        .findFirst().orElse("bulk 部分失败");
                throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                        "ES index 失败：" + first);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("ES bulkIndex failed", e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE,
                    "ES index 失败：" + e.getMessage());
        }
    }

    /** 清空某 doc 的 ES chunk 文档（delete_by_query term doc_id） */
    public void deleteByDoc(Long docId) {
        try {
            client.deleteByQuery(new DeleteByQueryRequest.Builder()
                    .index(props.getIndexName())
                    .query(Query.of(q -> q.term(t -> t.field("doc_id").value(docId))))
                    .refresh(true)
                    .build());
        } catch (Exception e) {
            log.warn("ES deleteByDoc failed docId={}", docId, e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE,
                    "ES 删除失败：" + e.getMessage());
        }
    }

    /** doc 失效同步：doc_status → 4（INVALIDATE；更新后 refresh 供对账即时可见） */
    public void invalidateDoc(Long docId) {
        try {
            client.updateByQuery(new UpdateByQueryRequest.Builder()
                    .index(props.getIndexName())
                    .query(Query.of(q -> q.term(t -> t.field("doc_id").value(docId))))
                    .script(s -> s
                            .lang("painless")
                            .source(src -> src.scriptString("ctx._source.doc_status = params.st"))
                            .params("st", JsonData.of(4)))
                    .refresh(true)
                    .build());
        } catch (Exception e) {
            log.warn("ES invalidateDoc failed docId={}", docId, e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE,
                    "ES 失效同步失败：" + e.getMessage());
        }
    }

    /**
     * 混合检索：kNN(embedding) + match(content, BM25) 双路各取候选窗 → <b>应用层 RRF</b> 融合 → Top-K。
     * <p>不用 ES 原生 RRF：{@code rank.rrf} 属铂金 license 功能，basic license 直接
     * {@code security_exception}（2026-10-29 裁决改应用层融合，RRF 公式与原 rankConstant=60 对齐）。
     * 出处 denormalize 自 ES 文档（chunk_id/doc_id/doc_title/doc_url/score），全链路不回 MySQL。
     */
    public List<EsSearchHit> search(List<Float> queryVector, String question, int topK) {
        try {
            int window = Math.max(topK * 4, 20);
            Query visible = Query.of(q -> q.term(t -> t.field("doc_status").value(2)));

            SearchRequest knnRequest = new SearchRequest.Builder()
                    .index(props.getIndexName())
                    .knn(List.of(new KnnSearch.Builder()
                            .field("embedding")
                            .queryVector(queryVector)
                            .k(window)
                            .numCandidates(Math.max(window * 10, 50))
                            .filter(visible)
                            .build()))
                    .size(window)
                    .allowNoIndices(true)
                    .expandWildcards(ExpandWildcard.None)
                    .build();
            SearchRequest bm25Request = new SearchRequest.Builder()
                    .index(props.getIndexName())
                    .query(Query.of(q -> q.bool(b -> b
                            .must(m -> m.match(mm -> mm.field("content").query(question)))
                            .filter(visible))))
                    .size(window)
                    .allowNoIndices(true)
                    .expandWildcards(ExpandWildcard.None)
                    .build();

            SearchResponse<Map> knnResp = client.search(knnRequest, Map.class);
            SearchResponse<Map> bm25Resp = client.search(bm25Request, Map.class);

            // 应用层 RRF：score(chunk) = Σ_r 1 / (k + rank_r)，k=60（对齐原 ES rrf rankConstant）
            final double rrfK = 60d;
            Map<String, EsSearchHit> hitByChunk = new LinkedHashMap<>();
            Map<String, Double> scores = new LinkedHashMap<>();
            accumulateRrf(knnResp, hitByChunk, scores, rrfK);
            accumulateRrf(bm25Resp, hitByChunk, scores, rrfK);

            return scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .map(e -> {
                        EsSearchHit h = hitByChunk.get(e.getKey());
                        return new EsSearchHit(h.chunkId(), h.docId(), h.docTitle(), h.docUrl(), h.content(), e.getValue());
                    })
                    .toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("ES search failed: {}", e.getMessage(), e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE);
        }
    }

    /** 单路检索结果按 RRF 公式累加得分（rank 从 1 起），并登记出处命中 */
    private void accumulateRrf(SearchResponse<Map> resp, Map<String, EsSearchHit> hitByChunk,
                               Map<String, Double> scores, double rrfK) {
        List<Hit<Map>> hits = resp.hits().hits();
        for (int i = 0; i < hits.size(); i++) {
            Hit<Map> hit = hits.get(i);
            Map<?, ?> src = hit.source();
            if (src == null) {
                continue;
            }
            String chunkId = String.valueOf(src.get("chunk_id"));
            scores.merge(chunkId, 1d / (rrfK + i + 1d), Double::sum);
            hitByChunk.putIfAbsent(chunkId, new EsSearchHit(
                    chunkId,
                    String.valueOf(src.get("doc_id")),
                    src.get("doc_title") == null ? "" : String.valueOf(src.get("doc_title")),
                    src.get("doc_url") == null ? "" : String.valueOf(src.get("doc_url")),
                    src.get("content") == null ? "" : String.valueOf(src.get("content")),
                    0d));
        }
    }

    /** 全量重建：删 index + 按契约重建（窗口内检索不可用，api-design.md §2.6） */
    public void recreateIndex() {
        try {
            BooleanResponse exists = client.indices().exists(e -> e.index(props.getIndexName()));
            if (exists.value()) {
                client.indices().delete(d -> d.index(props.getIndexName()));
            }
            client.indices().create(c -> c.index(props.getIndexName())
                    .withJson(new StringReader(INDEX_DEFINITION)));
        } catch (Exception e) {
            log.warn("ES recreateIndex failed", e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE,
                    "ES index 重建失败：" + e.getMessage());
        }
    }

    /** 确认 index 存在（不存在则按契约创建；幂等） */
    public void ensureIndex() {
        try {
            BooleanResponse exists = client.indices().exists(e -> e.index(props.getIndexName()));
            if (!exists.value()) {
                recreateIndex();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("ES ensureIndex failed", e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_SEARCH_UNAVAILABLE,
                    "ES 不可用：" + e.getMessage());
        }
    }

    private Map<String, Object> toMap(EsChunkDoc doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("chunk_id", doc.chunkId());
        map.put("doc_id", doc.docId());
        map.put("heading", doc.heading());
        map.put("content", doc.content());
        map.put("embedding", doc.embedding());
        map.put("source", doc.source());
        map.put("tags", doc.tags());
        map.put("doc_status", doc.docStatus());
        map.put("doc_title", doc.docTitle());
        map.put("doc_url", doc.docUrl());
        return map;
    }

    /** ES chunk 文档 */
    public record EsChunkDoc(String chunkId, Long docId, String heading, String content,
                             List<Float> embedding, Integer source, List<String> tags,
                             Integer docStatus, String docTitle, String docUrl) {
    }

    /** 检索命中（出处 denormalize） */
    public record EsSearchHit(String chunkId, String docId, String docTitle, String docUrl,
                              String content, double score) {
    }
}
