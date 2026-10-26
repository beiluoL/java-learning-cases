package com.ecommerce.learn.module.seckill.service;

import com.ecommerce.learn.module.seckill.dto.*;

import java.util.List;

public interface SeckillService {

    /** 进行中的秒杀活动列表（公开） */
    List<SeckillActivityVO> listActivities();

    /** 活动详情 */
    SeckillActivityVO detail(Long activityId);

    /** 参与秒杀（Lua 原子扣减 + 异步落库订单） */
    SeckillResultVO doSeckill(Long userId, Long activityId, Long skuId, int quantity);

    /** 后台保存活动 */
    Long saveActivity(SeckillActivitySaveDTO dto);

    /** 将库存预热到 Redis（活动开始前调用） */
    void publishStock(Long activityId);
}
