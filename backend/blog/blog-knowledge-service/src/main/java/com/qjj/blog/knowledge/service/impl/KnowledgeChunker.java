package com.qjj.blog.knowledge.service.impl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文章/文档切块器（blog-knowledge-base 提案 tasks 6.5）：
 * <ul>
 *   <li>按标题层级切节（# 行切换标题链），节内按段落聚合：满 500 字即出块，单块硬上限 800 字（超长段落硬切）；</li>
 *   <li>块文本带上下文增强前缀（文档标题 + 当前标题 + 分类/标签），标题链入 heading 列；</li>
 *   <li>token_count 估算：中文场景近似 1 字符 ≈ 1 token（展示用非精确）。</li>
 * </ul>
 */
@Component
public class KnowledgeChunker {

    /** 出块阈值（字符） */
    static final int TARGET_CHUNK_CHARS = 500;
    /** 单块硬上限（字符） */
    static final int MAX_CHUNK_CHARS = 800;

    /** 切块结果 */
    public record Chunk(int seq, String heading, String content, int tokenCount) {
    }

    /**
     * @param title    文档标题（前缀用）
     * @param markdown Markdown 正文
     * @param category 分类名（可空）
     * @param tags     标签列表（可空）
     */
    public List<Chunk> chunk(String title, String markdown, String category, List<String> tags) {
        List<Chunk> result = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return result;
        }
        AtomicInteger seq = new AtomicInteger();
        String prefixTitle = (title == null ? "" : title) + " > ";
        String metaSuffix = buildMetaSuffix(category, tags);

        StringBuilder buffer = new StringBuilder();
        StringBuilder para = new StringBuilder();
        String currentHeading = "";

        for (String rawLine : markdown.split("\n", -1)) {
            String trimmed = rawLine.trim();
            if (trimmed.startsWith("#") && trimmed.length() > 1 && isAtxHeading(trimmed)) {
                // 标题行：收尾段落 + 出块 + 切换标题链
                flushPara(buffer, para);
                emit(buffer, result, seq, currentHeading, prefixTitle, metaSuffix);
                currentHeading = trimmed.replaceFirst("^#+\\s*", "").trim();
                continue;
            }
            if (trimmed.isEmpty()) {
                // 段落边界
                flushPara(buffer, para);
                if (buffer.length() >= TARGET_CHUNK_CHARS) {
                    emit(buffer, result, seq, currentHeading, prefixTitle, metaSuffix);
                }
                continue;
            }
            para.append(trimmed).append('\n');
            if (para.length() >= MAX_CHUNK_CHARS) {
                // 超长段落硬切
                flushPara(buffer, para);
                emit(buffer, result, seq, currentHeading, prefixTitle, metaSuffix);
            } else if (buffer.length() + para.length() >= TARGET_CHUNK_CHARS) {
                flushPara(buffer, para);
                emit(buffer, result, seq, currentHeading, prefixTitle, metaSuffix);
            }
        }
        flushPara(buffer, para);
        emit(buffer, result, seq, currentHeading, prefixTitle, metaSuffix);
        return result;
    }

    private boolean isAtxHeading(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        // 仅接受 1-6 级且 # 后跟空白或行尾
        return level <= 6 && (level == line.length() || Character.isWhitespace(line.charAt(level)));
    }

    private void flushPara(StringBuilder buffer, StringBuilder para) {
        if (para.length() > 0) {
            buffer.append(para);
            para.setLength(0);
        }
    }

    /** 出块：超 800 字硬切为多块；内容含上下文增强前缀 */
    private void emit(StringBuilder buffer, List<Chunk> result, AtomicInteger seq,
                      String heading, String prefixTitle, String metaSuffix) {
        if (buffer.length() == 0) {
            return;
        }
        String body = buffer.toString();
        buffer.setLength(0);
        String headingPart = heading == null ? "" : heading;
        String prefix = prefixTitle + headingPart + metaSuffix;
        if (body.length() <= MAX_CHUNK_CHARS) {
            String content = prefix + "\n" + body;
            result.add(new Chunk(seq.getAndIncrement(), headingPart, content, content.length()));
            return;
        }
        for (int i = 0; i < body.length(); i += MAX_CHUNK_CHARS) {
            String part = body.substring(i, Math.min(body.length(), i + MAX_CHUNK_CHARS));
            String content = prefix + "\n" + part;
            result.add(new Chunk(seq.getAndIncrement(), headingPart, content, content.length()));
        }
    }

    /** 分类/标签增强后缀（拼进前缀行） */
    private String buildMetaSuffix(String category, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        if (category != null && !category.isBlank()) {
            sb.append(" | 分类：").append(category);
        }
        if (tags != null && !tags.isEmpty()) {
            sb.append(" | 标签：").append(String.join("、", tags));
        }
        return sb.toString();
    }
}
