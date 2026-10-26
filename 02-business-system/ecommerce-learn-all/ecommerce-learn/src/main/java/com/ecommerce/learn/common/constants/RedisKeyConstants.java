package com.ecommerce.learn.common.constants;

/**
 * Redis Key 设计规范：使用统一前缀，避免 key 冲突，便于过期与清理
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /** 用户令牌：login:token:{userId} -> jwt */
    public static final String USER_TOKEN = "login:token:%s";

    /** 商品详情缓存：product:detail:{spuId} */
    public static final String PRODUCT_DETAIL = "product:detail:%s";

    /** 商品详情缓存过期（秒） */
    public static final long PRODUCT_DETAIL_TTL = 600L;

    /** 分类树缓存：category:tree */
    public static final String CATEGORY_TREE = "category:tree";

    /** 分类树缓存过期（秒） */
    public static final long CATEGORY_TREE_TTL = 1800L;

    /** 秒杀库存：seckill:stock:{activityId}:{skuId} */
    public static final String SECKILL_STOCK = "seckill:stock:%s:%s";

    /** 秒杀已购用户集合：seckill:bought:{activityId} -> set(skuId:userId) */
    public static final String SECKILL_BOUGHT = "seckill:bought:%s";

    /** 秒杀下单幂等锁：seckill:lock:{activityId}:{userId}:{skuId} */
    public static final String SECKILL_LOCK = "seckill:lock:%s:%s:%s";

    /** 分布式限流：ratelimit:{key} */
    public static final String RATE_LIMIT = "ratelimit:%s";
}
