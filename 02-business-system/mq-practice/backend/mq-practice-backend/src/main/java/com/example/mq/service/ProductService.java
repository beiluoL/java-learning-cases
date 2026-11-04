package com.example.mq.service;

import com.example.mq.dto.ProductChangeRequest;
import com.example.mq.dto.ProductChangeResult;
import com.example.mq.dto.ProductView;

import java.util.List;

/**
 * 商品服务（场景三的事件源）。
 */
public interface ProductService {

    /** 商品列表（含 DB 库存 + Redis 秒杀库存） */
    List<ProductView> list();

    /** 单商品三视角快照：DB / Redis 缓存 / 检索索引 */
    ProductView snapshot(Long productId);

    /** 变更商品（上架/下架/改价）并发布事件 */
    ProductChangeResult change(Long productId, ProductChangeRequest request);
}
