package com.example.file.storage;

import java.io.InputStream;

/** 对象存储抽象：本地盘 / MinIO(S3) 可替换。 */
public interface ObjectStorage {

    void put(String objectKey, InputStream in, long size, String contentType);

    InputStream get(String objectKey);

    void remove(String objectKey);

    String publicUrl(String objectKey);

    String bucket();
}
