package com.qjj.blog.framework.markdown;

import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.List;

/**
 * Markdown → HTML 渲染器（服务端唯一转换点，business-design §E.5）。
 * <p>
 * 安全口径固化在本类、业务不可覆盖（§E.2「HTML 一律视为文本」）：
 * <ul>
 *   <li>{@code escapeHtml}：源文原生 HTML 一律转义为文本输出，不作为 HTML 执行</li>
 *   <li>URL 消毒（{@link SafeUrlAttributeProvider}）：仅 http/https 与相对路径可达，
 *       {@code javascript:} 等可执行协议不可达</li>
 *   <li>语法覆盖 §E.2 允许语法全集：CommonMark 核心（标题/段落/列表/引用/围栏代码/行内代码/
 *       链接/图片/粗斜体/分隔线）+ 表格扩展</li>
 * </ul>
 * 仅引 {@code flexmark} 核心与 {@code flexmark-ext-tables}（禁 flexmark-all 全家桶）。
 * Parser / HtmlRenderer 实例复用（线程安全），详情读取时转换，不落 .html 对象。
 */
public final class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet()
                .set(HtmlRenderer.ESCAPE_HTML, true)
                .set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options)
                .attributeProviderFactory(new SafeUrlAttributeProvider.Factory())
                .build();
    }

    /**
     * 渲染为 HTML。
     *
     * @param markdown Markdown 源文（OSS {@code text/markdown} 对象内容）
     * @return 渲染后的 HTML；入参 null/空白返回空串
     */
    public String renderToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
