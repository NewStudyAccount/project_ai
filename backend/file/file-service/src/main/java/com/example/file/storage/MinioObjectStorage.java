package com.example.file.storage;

import com.example.file.common.BizException;
import com.example.file.config.FileProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** MinIO / S3 兼容对象存储（凭证仅 file 服务持有）。 */
@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "minio")
public class MinioObjectStorage implements ObjectStorage {

    private final FileProperties.Storage storage;
    private final MinioClient client;

    public MinioObjectStorage(FileProperties properties) {
        this.storage = properties.getStorage();
        this.client = MinioClient.builder()
                .endpoint(storage.getEndpoint())
                .credentials(storage.getAccessKey(), storage.getSecretKey())
                .build();
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(storage.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception ex) {
            throw BizException.storage("对象存储写入失败");
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(storage.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw BizException.notFound();
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(storage.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw BizException.storage("对象存储删除失败");
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        String base = storage.getPublicBaseUrl();
        if (base == null || base.isBlank()) {
            String endpoint = storage.getEndpoint();
            if (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }
            base = endpoint + "/" + storage.getBucket();
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + objectKey;
    }

    @Override
    public String bucket() {
        return storage.getBucket();
    }
}
