package com.example.redislab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 库存实体，对应 stock 表。分布式锁场景以此演示并发扣减无超卖。
 */
@TableName("stock")
public class Stock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Integer count;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
}
