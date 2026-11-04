package com.example.redislab.core.bloom;

import jakarta.annotation.PostConstruct;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * 布隆过滤器封装（基于 Redisson RBloomFilter，底层是 Redis Bitmap）。
 *
 * <p>作用：缓存穿透防护第一道关卡。把「所有合法的商品 id」预热进过滤器，
 * 查询时先问布隆：不存在就直接返回，绝不查 DB（哪怕缓存也没有）。
 * 注意：布隆过滤器有「误判率」（fpp），可能把不存在的判定为存在，但不会把存在的判定为不存在。</p>
 */
@Service
public class BloomFilterService {

    private static final String FILTER_NAME = "penetration:bloom:product";

    private final RedissonClient redissonClient;
    private RBloomFilter<Long> productFilter;

    public BloomFilterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** 预热：初始化过滤器并灌入 1~100 的合法 id（expected=1000, 误判率=1%） */
    @PostConstruct
    public void init() {
        productFilter = redissonClient.getBloomFilter(FILTER_NAME);
        // tryInit 只会在首次真正初始化，后续实例调用不会重复建
        productFilter.tryInit(1000L, 0.01);
        for (long id = 1; id <= 100; id++) {
            productFilter.add(id);
        }
    }

    /** 是否「可能」包含该 id（false 一定不存在） */
    public boolean mightContain(Long id) {
        return productFilter.contains(id);
    }
}
