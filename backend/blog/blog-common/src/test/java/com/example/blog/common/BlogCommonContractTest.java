package com.example.blog.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.blog.common.audit.CurrentUser;
import com.example.blog.common.error.BizException;
import com.example.blog.common.error.CommonErrors;
import com.example.blog.common.page.PageQuery;
import com.example.blog.common.page.OrderWhitelist;
import com.example.blog.common.result.PageResult;
import com.example.blog.common.result.Result;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BlogCommonContractTest {

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void result_and_page_result_shape() {
        Result<String> ok = Result.ok("x");
        assertEquals(0, ok.code());
        assertEquals("x", ok.data());
        PageResult<String> page = PageResult.of(List.of("a"), 1, 10, 1);
        assertEquals(List.of("a"), page.records());
        assertEquals(1, page.total());
        assertEquals(10, page.size());
        assertEquals(1, page.current());
    }

    @Test
    void page_query_defaults_and_clamp() {
        PageQuery q = new PageQuery();
        assertEquals(1, q.getCurrent());
        assertEquals(10, q.getSize());
        q.setSize(1000);
        assertEquals(100, q.getSize());
        q.setCurrent(-5);
        assertEquals(1, q.getCurrent());
    }

    @Test
    void order_whitelist_rejects_unknown() {
        assertEquals("createTime", OrderWhitelist.requireAllowed("createTime", Set.of("createTime")));
        BizException ex = assertThrows(BizException.class,
                () -> OrderWhitelist.requireAllowed("id;drop", Set.of("createTime")));
        assertEquals(CommonErrors.BAD_PARAM.code(), ex.getCode());
    }

    @Test
    void forbidden_is_403_semantic() {
        BizException ex = BizException.forbidden();
        assertEquals(403, ex.getCode());
        BizException un = BizException.unauthorized();
        assertEquals(401, un.getCode());
    }

    @Test
    void fourteen_digit_id_format() {
        String id = "260922" + String.format("%08d", 1L);
        assertEquals(14, id.length());
        assertEquals("26092200000001", id);
        assertTrue(Long.parseLong(id) > 0);
    }
}
