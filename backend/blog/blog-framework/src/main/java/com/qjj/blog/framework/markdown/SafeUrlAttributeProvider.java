package com.qjj.blog.framework.markdown;

import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.html.Attribute;
import com.vladsch.flexmark.util.html.MutableAttributes;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 链接/图片 URL 消毒（business-design §E.2「禁 javascript: 等可执行协议」）。
 * <p>
 * 仅 {@code http}/{@code https} 与相对路径可达；其余协议（{@code javascript:}、{@code data:}、
 * {@code vbscript:} 等）直接移除属性。先剥离空白/控制符再判协议，防 {@code "java\tscript:"} 式走私。
 * flexmark 0.64.x 无 {@code SANITIZE_URLS} 渲染选项，URL 消毒经 AttributeProvider 扩展点固化。
 */
final class SafeUrlAttributeProvider implements AttributeProvider {

    private static final Pattern SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.\\-]*):");
    private static final String[] URL_ATTRS = {"href", "src"};

    @Override
    public void setAttributes(Node node, AttributablePart part, MutableAttributes attributes) {
        for (String name : URL_ATTRS) {
            if (!attributes.contains(name)) {
                continue;
            }
            if (!isSafeUrl(attributes.getValue(name))) {
                Attribute attr = attributes.get(name);
                if (attr != null) {
                    attributes.remove(attr);
                }
            }
        }
    }

    /** http/https 显式协议或相对路径（无协议）视为安全，其余协议一律拒绝。 */
    private static boolean isSafeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        String cleaned = url.replaceAll("[\\p{Cntrl}\\s]", "");
        Matcher matcher = SCHEME.matcher(cleaned);
        if (matcher.find()) {
            String scheme = matcher.group(1).toLowerCase(Locale.ROOT);
            return "http".equals(scheme) || "https".equals(scheme);
        }
        return true;
    }

    /** 渲染器注册用工厂（Dependent 无依赖、不影响全局作用域）。 */
    static final class Factory implements AttributeProviderFactory {

        @Override
        public AttributeProvider apply(LinkResolverContext context) {
            return new SafeUrlAttributeProvider();
        }

        @Override
        public Set<Class<?>> getAfterDependents() {
            return Collections.emptySet();
        }

        @Override
        public Set<Class<?>> getBeforeDependents() {
            return Collections.emptySet();
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }
    }
}
