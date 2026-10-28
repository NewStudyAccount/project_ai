package com.qjj.blog.knowledge.service;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.knowledge.dto.DocPageQuery;
import com.qjj.blog.knowledge.dto.FromArticlesRequest;
import com.qjj.blog.knowledge.vo.FromArticlesResultVO;
import com.qjj.blog.knowledge.vo.ImportDocsResultVO;
import com.qjj.blog.knowledge.vo.KnowledgeDocVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 知识文档服务（api-design.md §1.4/§2.1–§2.6）
 */
public interface KnowledgeDocService {

    /** 从文章入库（≤100 条/次；受理即返回，向量化异步） */
    FromArticlesResultVO fromArticles(FromArticlesRequest request);

    /** 手动导入（multipart：files ≤20 个 .md、单文件 ≤10MB、tags?；一文件一 doc） */
    ImportDocsResultVO importDocs(MultipartFile[] files, String tags);

    /** 编辑手动导入（仅 source=2；重传触发 REVECTORIZE；203095） */
    Map<String, Object> updateDoc(String id, MultipartFile file, String tags);

    /** 删除知识文档（仅 source=2；逻辑删 + INVALIDATE 清 ES） */
    Map<String, Object> deleteDoc(String id);

    /** 单文档重建（重切+重向量化；203090/203093） */
    Map<String, Object> revectorize(String id);

    /** 全量重建索引（FULL_REINDEX；出参 taskId/docTotal） */
    Map<String, Object> reindex();

    /** 知识文档分页（含 readyChunkCount 向量健康度） */
    PageResult<KnowledgeDocVO> pageDocs(DocPageQuery query);
}
