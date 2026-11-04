package com.example.redislab.service.impl;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.entity.Product;
import com.example.redislab.mapper.ProductMapper;
import com.example.redislab.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 缓存加速（Cache-Aside 模式）实现。
 *
 * <p>读路径：cache → miss → DB → 回填 cache。
 * 写路径：更新 DB → 删除 cache（不是更新 cache）。
 * 为什么「删」而非「更」？并发场景下「先更新缓存」会出现：线程A更新DB、线程B更新DB、
 * 线程B写缓存、线程A写缓存，导致缓存里是A的旧值。删缓存通过下次读自动重建，实现简单且最终一致。</p>
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StatService statService;

    public ProductServiceImpl(ProductMapper productMapper,
                             RedisTemplate<String, Object> redisTemplate,
                             StatService statService) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
        this.statService = statService;
    }

    @Override
    public Product getByIdWithCache(Long id) {
        String key = RedisKeys.cacheProduct(id);
        Product cached = (Product) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            statService.incr("cache:hit");
            return cached;
        }
        statService.incr("cache:miss");
        Product db = productMapper.selectById(id);
        if (db != null) {
            // 5 分钟基础 TTL + 0~60s 随机抖动，打散失效时间，避免缓存雪崩
            long ttl = RedisKeys.ttlWithJitter(300, 60);
            redisTemplate.opsForValue().set(key, db, Duration.ofSeconds(ttl));
            statService.incr("cache:db");
        }
        return db;
    }

    @Override
    public Product update(Long id, String name, BigDecimal price) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("商品不存在: " + id);
        }
        p.setName(name);
        p.setPrice(price);
        productMapper.updateById(p);
        // Cache-Aside：更新 DB 后删除缓存
        redisTemplate.delete(RedisKeys.cacheProduct(id));
        statService.incr("cache:update");
        return p;
    }

    @Override
    public void deleteCache(Long id) {
        redisTemplate.delete(RedisKeys.cacheProduct(id));
    }
}
