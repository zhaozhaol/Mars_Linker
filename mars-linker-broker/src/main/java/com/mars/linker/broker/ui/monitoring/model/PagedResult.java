package com.mars.linker.broker.ui.monitoring.model;

import java.util.List;

/**
 * 分页查询结果。
 */
public class PagedResult<T> {

    /** 当前页数据列表。 */
    private List<T> items;
    /** 总记录数。 */
    private long totalCount;
    /** 当前页码（从 1 开始）。 */
    private int page;
    /** 每页大小。 */
    private int size;

    public PagedResult() {}

    public PagedResult(List<T> items, long totalCount, int page, int size) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.size = size;
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
