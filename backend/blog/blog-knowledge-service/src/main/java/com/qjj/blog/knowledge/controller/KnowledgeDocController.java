package com.qjj.blog.knowledge.controller;

import com.qjj.blog.common.result.PageResult;
import com.qjj.blog.common.result.Result;
import com.qjj.blog.knowledge.dto.DocPageQuery;
import com.qjj.blog.knowledge.service.KnowledgeDocService;
import com.qjj.blog.knowledge.vo.ImportDocsResultVO;
import com.qjj.blog.knowledge.vo.KnowledgeDocVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 管理端 · 知识文档 API（api-design.md §2.1–§2.6；权限位 blog:knowledge:*，网关鉴权）
 */
@Tag(name = "知识库 · 知识文档", description = "分页/手动导入/编辑/删除/重建/全量重建")
@RestController
@RequestMapping("/api/v1/admin/knowledge/docs")
@RequiredArgsConstructor
public class KnowledgeDocController {

    private final KnowledgeDocService docService;

    @Operation(summary = "知识文档分页（含 readyChunkCount 向量健康度）")
    @GetMapping
    public Result<PageResult<KnowledgeDocVO>> page(DocPageQuery query) {
        return Result.ok(docService.pageDocs(query));
    }

    @Operation(summary = "手动导入（≤20 个 .md、单文件 ≤10MB；一文件一 doc）")
    @PostMapping
    public Result<ImportDocsResultVO> importDocs(@RequestParam("files") MultipartFile[] files,
                                                 @RequestParam(value = "tags", required = false) String tags) {
        return Result.ok(docService.importDocs(files, tags));
    }

    @Operation(summary = "编辑手动导入（仅 source=2；重传触发 REVECTORIZE）")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable String id,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              @RequestParam(value = "tags", required = false) String tags) {
        return Result.ok(docService.updateDoc(id, file, tags));
    }

    @Operation(summary = "删除知识文档（仅 source=2；逻辑删 + INVALIDATE 清 ES）")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable String id) {
        return Result.ok(docService.deleteDoc(id));
    }

    @Operation(summary = "单文档重建（重切+重向量化+ES 重 index）")
    @PostMapping("/{id}/revectorize")
    public Result<Map<String, Object>> revectorize(@PathVariable String id) {
        return Result.ok(docService.revectorize(id));
    }

    @Operation(summary = "全量重建索引（删 ES index 重建；窗口内检索不可用）")
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        return Result.ok(docService.reindex());
    }
}
