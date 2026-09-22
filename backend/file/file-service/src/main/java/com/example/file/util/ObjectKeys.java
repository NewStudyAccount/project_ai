package com.example.file.util;

import com.example.file.common.BizException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 对象键与 content-type 校验。 */
public final class ObjectKeys {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/-]{0,249}$");
    private static final Set<String> BLOCKED_SEGMENTS = Set.of("..", ".");

    private ObjectKeys() {
    }

    public static void requireSafeKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw BizException.badParam("objectKey 不能为空");
        }
        if (objectKey.contains("\\") || objectKey.contains("//") || !SAFE_KEY.matcher(objectKey).matches()) {
            throw BizException.badParam("objectKey 非法");
        }
        for (String seg : objectKey.split("/")) {
            if (BLOCKED_SEGMENTS.contains(seg) || seg.isBlank()) {
                throw BizException.badParam("objectKey 非法");
            }
        }
    }

    public static void requireAllowedContentType(String contentType, Set<String> allowed) {
        String normalized = normalizeContentType(contentType);
        if (normalized.isEmpty() || !allowed.contains(normalized)) {
            throw BizException.typeNotAllowed();
        }
    }

    public static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String ct = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        return semi >= 0 ? ct.substring(0, semi).trim() : ct;
    }

    /** 生成唯一键：{biz}/{bizId}/{scene}/{uuid}.{ext} */
    public static String generateKey(String bizType, String bizId, String scene, String filename) {
        if (bizType == null || bizType.isBlank() || scene == null || scene.isBlank()) {
            throw BizException.badParam("bizType/scene 不能为空");
        }
        String safeBiz = sanitizeSegment(bizType);
        String safeScene = sanitizeSegment(scene);
        String safeBizId = (bizId == null || bizId.isBlank()) ? "0" : sanitizeSegment(bizId);
        String ext = extension(filename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String name = ext.isEmpty() ? uuid : uuid + "." + ext;
        return safeBiz + "/" + safeBizId + "/" + safeScene + "/" + name;
    }

    public static String sanitizeSegment(String raw) {
        String s = raw.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (s.isEmpty()) {
            throw BizException.badParam("路径段非法");
        }
        return s;
    }

    public static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,16}") ? ext : "";
    }
}
