package com.example.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.file.common.BizException;
import com.example.file.config.FileProperties;
import com.example.file.entity.FileObject;
import com.example.file.mapper.FileObjectMapper;
import com.example.file.service.FileObjectService;
import com.example.file.service.IdService;
import com.example.file.storage.ObjectStorage;
import com.example.file.util.ObjectKeys;
import com.example.file.vo.FileObjectVo;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

/** 纯单测：白名单拒绝、元数据落库、同 key 覆盖。 */
class FileObjectServiceTest {

    private FileObjectMapper mapper;
    private IdService idService;
    private ObjectStorage storage;
    private FileProperties properties;
    private FileObjectService service;

    @BeforeEach
    void setUp() {
        mapper = mock(FileObjectMapper.class);
        idService = mock(IdService.class);
        storage = mock(ObjectStorage.class);
        properties = new FileProperties();
        properties.getUpload().setMaxSizeBytes(1024);
        properties.getUpload().setAllowedContentTypes(List.of("image/png", "text/html"));
        when(storage.bucket()).thenReturn("file-bucket");
        when(storage.publicUrl(anyString())).thenAnswer(inv -> "http://cdn/" + inv.getArgument(0));
        service = new FileObjectService(mapper, idService, storage, properties);
    }

    @Test
    void reject_content_type_not_in_whitelist() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.exe", "application/octet-stream", new byte[] {1});
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file, "blog", "1", "assets", null));
        assertEquals(20001, ex.getCode());
        verify(mapper, never()).insert(any(FileObject.class));
    }

    @Test
    void reject_too_large() {
        properties.getUpload().setMaxSizeBytes(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[] {1, 2, 3});
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file, "blog", "1", "assets", null));
        assertEquals(20002, ex.getCode());
    }

    @Test
    void upload_writes_metadata_and_object() throws Exception {
        when(idService.nextId("file_object")).thenReturn(26092200000001L);
        when(mapper.selectByObjectKeyAny(any())).thenReturn(null);
        when(mapper.insert(any(FileObject.class))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile(
                "file", "pic.png", "image/png", new byte[] {1, 2, 3});
        FileObjectVo vo = service.upload(file, "blog_post", "26092200000001", "assets", null);

        assertEquals("image/png", vo.contentType());
        assertEquals(3L, vo.size());
        assertTrue(vo.objectKey().startsWith("blog_post/26092200000001/assets/"));
        assertTrue(vo.objectKey().endsWith(".png"));
        verify(mapper).insert(any(FileObject.class));
        verify(storage).put(eq(vo.objectKey()), any(), eq(3L), eq("image/png"));
    }

    @Test
    void overwrite_same_object_key_updates_metadata() throws Exception {
        FileObject existing = new FileObject();
        existing.setId(26092200000002L);
        existing.setObjectKey("blog/posts/26092200000001/content.html");
        existing.setDeleted(0);
        when(mapper.selectByObjectKeyAny("blog/posts/26092200000001/content.html")).thenReturn(existing);
        when(mapper.updateMetaRevive(any(FileObject.class))).thenReturn(1);

        MockMultipartFile file = new MockMultipartFile(
                "file", "content.html", "text/html", "<p>v2</p>".getBytes());
        FileObjectVo vo = service.upload(
                file, "blog_post", "26092200000001", "content",
                "blog/posts/26092200000001/content.html");

        assertEquals("blog/posts/26092200000001/content.html", vo.objectKey());
        assertEquals(26092200000002L, vo.id());
        ArgumentCaptor<FileObject> captor = ArgumentCaptor.forClass(FileObject.class);
        verify(mapper).updateMetaRevive(captor.capture());
        assertEquals(9L, captor.getValue().getSize());
        assertEquals(0, captor.getValue().getDeleted());
        verify(mapper, never()).insert(any(FileObject.class));
        verify(storage).put(eq("blog/posts/26092200000001/content.html"), any(), eq(9L), eq("text/html"));
    }

    @Test
    void list_by_biz_returns_metadata() {
        FileObject row = new FileObject();
        row.setId(1L);
        row.setObjectKey("blog/posts/1/content.html");
        row.setBizType("blog_post");
        row.setBizId("1");
        row.setContentType("text/html");
        row.setSize(10L);
        when(mapper.selectList(any())).thenReturn(List.of(row));

        List<FileObjectVo> list = service.listByBiz("blog_post", "1");
        assertEquals(1, list.size());
        assertEquals("blog/posts/1/content.html", list.get(0).objectKey());
    }

    @Test
    void object_key_rejects_path_traversal() {
        BizException ex = assertThrows(BizException.class,
                () -> ObjectKeys.requireSafeKey("../etc/passwd"));
        assertEquals(30001, ex.getCode());
    }

    @Test
    void content_type_whitelist_set_is_case_insensitive() {
        Set<String> allowed = Set.of("image/png");
        assertThrows(BizException.class,
                () -> ObjectKeys.requireAllowedContentType("image/jpeg", allowed));
        ObjectKeys.requireAllowedContentType("image/png; charset=utf-8", allowed);
    }
}
