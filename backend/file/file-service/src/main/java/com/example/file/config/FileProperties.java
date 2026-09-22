package com.example.file.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件服务配置。生产密钥通过环境变量/Nacos 注入，禁止写入仓库。
 */
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    private final Auth auth = new Auth();
    private final Upload upload = new Upload();
    private final Storage storage = new Storage();

    public Auth getAuth() {
        return auth;
    }

    public Upload getUpload() {
        return upload;
    }

    public Storage getStorage() {
        return storage;
    }

    /** JWT 验签（与 auth 同 iss/密钥） */
    public static class Auth {
        private String issuer = "auth-service";
        private String jwtSecret = "change-me-local-dev-secret-not-for-prod";

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }

    /** 上传白名单与大小限制 */
    public static class Upload {
        private long maxSizeBytes = 10L * 1024 * 1024;
        private List<String> allowedContentTypes = new ArrayList<>(List.of(
                "image/png", "image/jpeg", "image/gif", "image/webp",
                "application/pdf", "text/plain", "text/markdown", "text/html",
                "application/octet-stream"
        ));

        public long getMaxSizeBytes() {
            return maxSizeBytes;
        }

        public void setMaxSizeBytes(long maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
        }

        public List<String> getAllowedContentTypes() {
            return allowedContentTypes;
        }

        public void setAllowedContentTypes(List<String> allowedContentTypes) {
            this.allowedContentTypes = allowedContentTypes;
        }
    }

    /** 对象存储（local / minio） */
    public static class Storage {
        /** local | minio */
        private String type = "local";
        private String bucket = "file-bucket";
        /** 公开访问基础 URL（可指向 Nginx/CDN）；签名 URL 接口预留 */
        private String publicBaseUrl = "http://127.0.0.1:8082/file/objects/content";
        private String localRoot = "./data/objects";
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "";
        private String secretKey = "";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
