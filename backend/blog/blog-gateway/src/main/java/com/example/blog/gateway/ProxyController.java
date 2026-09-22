package com.example.blog.gateway;

import com.example.blog.common.auth.UserHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 将 /api/** 转发到 blog-content-service，并透传身份头。 */
@RestController
public class ProxyController {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String contentBaseUrl;

    public ProxyController(@Value("${blog.content.base-url:http://127.0.0.1:8084}") String contentBaseUrl) {
        this.contentBaseUrl = contentBaseUrl.endsWith("/")
                ? contentBaseUrl.substring(0, contentBaseUrl.length() - 1)
                : contentBaseUrl;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException, InterruptedException {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String target = contentBaseUrl + path + (query == null ? "" : "?" + query);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                .timeout(java.time.Duration.ofSeconds(15));
        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
        byte[] body = request.getInputStream().readAllBytes();
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        }

        java.util.Collections.list(request.getHeaderNames()).forEach(name -> {
            if (name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length")
                    || name.equalsIgnoreCase("connection")) {
                return;
            }
            String value = request.getHeader(name);
            if (value != null) {
                builder.header(name, value);
            }
        });
        String uid = request.getHeader(UserHeaders.USER_ID);
        if (uid != null) {
            builder.setHeader(UserHeaders.USER_ID, uid);
        }
        String username = request.getHeader(UserHeaders.USERNAME);
        if (username != null) {
            builder.setHeader(UserHeaders.USERNAME, username);
        }

        HttpResponse<byte[]> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((k, v) -> {
            if (!k.equalsIgnoreCase("transfer-encoding") && !k.equalsIgnoreCase("content-length")) {
                headers.addAll(k, v == null ? Collections.emptyList() : v);
            }
        });
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
    }

    @SuppressWarnings("unused")
    private static List<String> empty() {
        return List.of();
    }
}
