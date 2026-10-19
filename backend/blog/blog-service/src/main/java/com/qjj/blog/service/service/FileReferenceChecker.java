package com.qjj.blog.service.service;

import java.util.List;

/**
 * 删除前引用检查扩展点（object-storage-design §3.5）。
 * 业务模块各自实现，不在此硬编码业务表；FileService 删除前调用全部实现。
 */
public interface FileReferenceChecker {

    FileReferenceResult check(Long fileId);

    final class FileReferenceResult {
        private final boolean referenced;
        private final List<String> refs;

        private FileReferenceResult(boolean referenced, List<String> refs) {
            this.referenced = referenced;
            this.refs = refs == null ? List.of() : List.copyOf(refs);
        }

        public static FileReferenceResult none() {
            return new FileReferenceResult(false, List.of());
        }

        public static FileReferenceResult of(List<String> refs) {
            return new FileReferenceResult(refs != null && !refs.isEmpty(), refs);
        }

        public boolean isReferenced() {
            return referenced;
        }

        public List<String> getRefs() {
            return refs;
        }
    }
}
