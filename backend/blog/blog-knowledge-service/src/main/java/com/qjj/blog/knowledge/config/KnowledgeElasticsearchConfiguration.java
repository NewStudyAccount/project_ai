package com.qjj.blog.knowledge.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;

/**
 * ES client 装配（blog-knowledge-base 提案 tasks 3.4）。
 * CLAUDE.md §2.1 例外条款：检索引擎 ES client 唯一落点为 blog-knowledge-service，framework 不引入。
 * ES 9.5.5 Java client 传输层为 {@link Rest5Client}（elasticsearch-rest5-client，随 elasticsearch-java 9.5.5 传递）。
 */
@Configuration
@EnableConfigurationProperties({KnowledgeElasticsearchProperties.class, KnowledgeAiYmlProperties.class})
public class KnowledgeElasticsearchConfiguration {

    @Bean(destroyMethod = "close")
    public Rest5Client knowledgeRest5Client(KnowledgeElasticsearchProperties props) {
        List<String> uris = props.getUris();
        if (uris == null || uris.isEmpty()) {
            throw new IllegalStateException(
                    "blog.knowledge.elasticsearch.uris 未配置，知识库模块无法启动（参照 application-local.yml 示例）");
        }
        HttpHost[] hosts = uris.stream().map(KnowledgeElasticsearchConfiguration::toHost).toArray(HttpHost[]::new);
        var builder = Rest5Client.builder(hosts)
                .setConnectionConfigCallback(connectionConfig -> {
                    connectionConfig.setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeout().toMillis()));
                    connectionConfig.setSocketTimeout(Timeout.ofMilliseconds(props.getSocketTimeout().toMillis()));
                })
                .setRequestConfigCallback(requestConfig ->
                        requestConfig.setResponseTimeout(Timeout.ofMilliseconds(props.getSocketTimeout().toMillis())));
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(new AuthScope(null, -1),
                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword().toCharArray()));
            builder.setHttpClientConfigCallback(httpClient -> {
                httpClient.setDefaultCredentialsProvider(credentials);
            });
        }
        return builder.build();
    }

    @Bean
    public ElasticsearchClient knowledgeElasticsearchClient(Rest5Client knowledgeRest5Client) {
        return new ElasticsearchClient(new Rest5ClientTransport(knowledgeRest5Client, new JacksonJsonpMapper()));
    }

    private static HttpHost toHost(String uri) {
        URI parsed = URI.create(uri.trim());
        String scheme = parsed.getScheme() == null ? "http" : parsed.getScheme();
        int port = parsed.getPort() == -1 ? ("https".equals(scheme) ? 443 : 9200) : parsed.getPort();
        return new HttpHost(scheme, parsed.getHost(), port);
    }
}
