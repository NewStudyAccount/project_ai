package com.example.blog.common.page;

/** 分页入参契约。 */
public class PageQuery {

    private long current = 1L;
    private long size = 10L;
    /** 排序字段，必须经白名单校验 */
    private String orderBy = "";
    /** asc / desc */
    private String order = "desc";

    public long getCurrent() {
        return current < 1 ? 1 : current;
    }

    public void setCurrent(long current) {
        this.current = current;
    }

    public long getSize() {
        if (size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getOrderBy() {
        return orderBy == null ? "" : orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrder() {
        if (order == null || order.isBlank()) {
            return "desc";
        }
        return order.equalsIgnoreCase("asc") ? "asc" : "desc";
    }

    public void setOrder(String order) {
        this.order = order;
    }
}
