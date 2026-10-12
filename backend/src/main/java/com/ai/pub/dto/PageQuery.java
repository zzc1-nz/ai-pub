package com.ai.pub.dto;

/**
 * 分页查询参数。
 *
 * 说明：原先还有 keyword 字段与三参构造器，但全项目没有任何调用方
 * （控制器都只用默认的 pageNum/pageSize），已删除。
 * 前端若要按关键词检索，应通过各接口自己的查询参数实现
 * （如 /api/wechat/messages 的 openid / msgType / 时间区间）。
 */
public class PageQuery {

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    public PageQuery() {
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
