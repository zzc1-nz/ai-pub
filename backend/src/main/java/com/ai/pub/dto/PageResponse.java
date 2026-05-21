package com.ai.pub.dto;

import java.util.List;

public class PageResponse<T> {

    private List<T> records;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;

    public PageResponse() {
    }

    public PageResponse(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResponse<T> of(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        return new PageResponse<T>(records, total, pageNum, pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
