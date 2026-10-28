package com.qjj.blog.knowledge.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qjj.blog.common.enums.BlogErrorCodeEnum;
import com.qjj.blog.common.exception.BizException;
import com.qjj.blog.framework.concurrent.KnowledgeAiProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SiliconFlow OpenAI 兼容客户端（blog-knowledge-base 提案 tasks 4.2）。
 * <ul>
 *   <li>embed：POST /embeddings（批量 input）；读超时 embed-read-timeout。</li>
 *   <li>chatStream：POST /chat/completions stream=true；逐行解析 SSE data 帧，取 choices[0].delta.content；
 *       流式不设整体超时（由调用方 15s 心跳与连接层兜底）。</li>
 * </ul>
 * 实例为值对象（baseUrl/apiKey/model/dim + 超时），由 {@link AiProviderResolver} 按 DB/yml 配置构建；
 * java.net.http.HttpClient 复用 JVM 内连接池（无新依赖，业务禁止自建线程池——本类无自管线程）。
 */
@Slf4j
public class SiliconFlowClient implements EmbeddingProvider, LLMProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final HttpClient httpClient;

    private final Duration embedReadTimeout;

    public SiliconFlowClient(String baseUrl, String apiKey, String model, KnowledgeAiProperties aiProps) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.siliconflow.cn/v1" : trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.embedReadTimeout = aiProps.getEmbedReadTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(aiProps.getConnectTimeout())
                .build();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", texts);
            HttpRequest request = jsonRequest("/embeddings", MAPPER.writeValueAsString(body))
                    .timeout(embedReadTimeout)
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                log.warn("SiliconFlow embed failed status={} body={}", resp.statusCode(), abbreviate(resp.body()));
                throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                        BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED.getDesc() + "：HTTP " + resp.statusCode());
            }
            JsonNode data = MAPPER.readTree(resp.body()).path("data");
            List<float[]> vectors = new ArrayList<>(texts.size());
            for (JsonNode item : data) {
                JsonNode arr = item.path("embedding");
                float[] v = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    v[i] = (float) arr.get(i).asDouble();
                }
                vectors.add(v);
            }
            if (vectors.size() != texts.size()) {
                throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                        BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED.getDesc() + "：返回向量数与输入不一致");
            }
            return vectors;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SiliconFlow embed error model={}", model, e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED,
                    BlogErrorCodeEnum.KNOWLEDGE_EMBED_FAILED.getDesc() + "：" + e.getMessage());
        }
    }

    @Override
    public String chatStream(String prompt, Consumer<String> deltaConsumer) {
        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(message));
            body.put("stream", true);
            HttpRequest request = jsonRequest("/chat/completions", MAPPER.writeValueAsString(body))
                    .header("Accept", "text/event-stream")
                    .build();
            // ofInputStream：响应边到边读，SSE 分片实时回调（不缓存整流）
            HttpResponse<java.io.InputStream> resp =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("SiliconFlow chat failed status={} body={}", resp.statusCode(), abbreviate(err));
                throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_LLM_UNAVAILABLE,
                        BlogErrorCodeEnum.KNOWLEDGE_LLM_UNAVAILABLE.getDesc() + "：HTTP " + resp.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.isEmpty() || "[DONE]".equals(payload)) {
                        if ("[DONE]".equals(payload)) {
                            break;
                        }
                        continue;
                    }
                    JsonNode node = MAPPER.readTree(payload);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        deltaConsumer.accept(delta);
                    }
                }
            }
            return model;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SiliconFlow chatStream error model={}", model, e);
            throw new BizException(BlogErrorCodeEnum.KNOWLEDGE_LLM_UNAVAILABLE,
                    BlogErrorCodeEnum.KNOWLEDGE_LLM_UNAVAILABLE.getDesc() + "：" + e.getMessage());
        }
    }

    /** 连通测试（管理端 test）：Embedding 走 1 条 4 字文本；LLM 走 max_tokens=1 非流式对话 */
    public long ping(boolean embedding) {
        long start = System.currentTimeMillis();
        if (embedding) {
            embed(List.of("连通测试"));
        } else {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", model);
                body.put("messages", List.of(Map.of("role", "user", "content", "hi")));
                body.put("max_tokens", 1);
                body.put("stream", false);
                HttpRequest request = jsonRequest("/chat/completions", MAPPER.writeValueAsString(body))
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED,
                            BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED.getDesc() + "：HTTP " + resp.statusCode());
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                throw new BizException(BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED,
                        BlogErrorCodeEnum.AI_PROVIDER_TEST_FAILED.getDesc() + "：" + e.getMessage());
            }
        }
        return System.currentTimeMillis() - start;
    }

    private HttpRequest.Builder jsonRequest(String path, String json) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
