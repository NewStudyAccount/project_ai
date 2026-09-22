package com.example.file.storage;

import com.example.file.common.BizException;
import com.example.file.config.FileProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 本地文件系统对象存储（local/test）。 */
@Component
@ConditionalOnProperty(prefix = "file.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStorage {

    private final FileProperties.Storage storage;

    public LocalObjectStorage(FileProperties properties) {
        this.storage = properties.getStorage();
    }

    @Override
    public void put(String objectKey, InputStream in, long size, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                in.transferTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BizException.storage("本地对象写入失败");
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Path target = resolve(objectKey);
        try {
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw BizException.notFound();
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException ex) {
            throw BizException.storage("本地对象删除失败");
        }
    }

    @Override
    public String publicUrl(String objectKey) {
        String base = storage.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + objectKey;
    }

    @Override
    public String bucket() {
        return storage.getBucket();
    }

    private Path resolve(String objectKey) {
        Path root = Paths.get(storage.getLocalRoot(), storage.getBucket()).toAbsolutePath().normalize();
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw BizException.badParam("objectKey 非法");
        }
        return target;
    }
}
