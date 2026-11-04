package com.example.redislab.service;

import com.example.redislab.entity.Product;

import java.math.BigDecimal;

public interface ProductService {

    /** 读：缓存命中直接返回，未命中查 DB 并回填（带随机 TTL 防雪崩） */
    Product getByIdWithCache(Long id);

    /** 写：更新 DB 后删除缓存，保证 Cache-Aside 最终一致 */
    Product update(Long id, String name, BigDecimal price);

    /** 主动删除缓存 */
    void deleteCache(Long id);
}
