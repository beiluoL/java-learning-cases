package com.ecommerce.learn.module.seckill.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.constants.RedisKeyConstants;
import com.ecommerce.learn.common.exception.Assert;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.common.util.RedisUtil;
import com.ecommerce.learn.module.seckill.dto.*;
import com.ecommerce.learn.module.seckill.entity.SeckillActivity;
import com.ecommerce.learn.module.seckill.mapper.SeckillActivityMapper;
import com.ecommerce.learn.module.seckill.service.SeckillService;
import com.ecommerce.learn.module.seckill.service.SeckillAsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀服务：Redis + Lua 原子扣减，解决高并发超卖
 */
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final RedisUtil redisUtil;
    private final RedisScript<Long> seckillScript;
    private final SeckillAsyncService seckillAsyncService;

    @Override
    public List<SeckillActivityVO> listActivities() {
        return activityMapper.selectList(Wrappers.<SeckillActivity>lambdaQuery()
                        .eq(SeckillActivity::getStatus, 1))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public SeckillActivityVO detail(Long activityId) {
        SeckillActivity act = activityMapper.selectById(activityId);
        Assert.notNull(act, "活动不存在");
        return toVO(act);
    }

    @Override
    public SeckillResultVO doSeckill(Long userId, Long activityId, Long skuId, int quantity) {
        SeckillActivity act = activityMapper.selectById(activityId);
        Assert.notNull(act, "活动不存在");
        if (act.getStatus() != 1) {
            throw new BizException(CodeEnum.BIZ_ERROR, "活动未开始或已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        if (act.getStartTime() != null && now.isBefore(act.getStartTime())) {
            throw new BizException(CodeEnum.BIZ_ERROR, "活动未开始");
        }
        if (act.getEndTime() != null && now.isAfter(act.getEndTime())) {
            throw new BizException(CodeEnum.BIZ_ERROR, "活动已结束");
        }
        if (!act.getSkuId().equals(skuId)) {
            throw new BizException(CodeEnum.BIZ_ERROR, "SKU 与活动不匹配");
        }

        // 库存预热：若 Redis 中无库存，则按 DB 库存初始化，TTL 到活动结束
        String stockKey = String.format(RedisKeyConstants.SECKILL_STOCK, activityId, skuId);
        String boughtKey = String.format(RedisKeyConstants.SECKILL_BOUGHT, activityId);
        if (!redisUtil.hasKey(stockKey)) {
            long ttl = act.getEndTime() != null
                    ? Duration.between(now, act.getEndTime()).getSeconds()
                    : 3600;
            redisUtil.set(stockKey, String.valueOf(act.getStock()), Math.max(ttl, 60));
        }

        String userKey = skuId + ":" + userId;
        Long result = redisUtil.executeScript(seckillScript,
                List.of(stockKey, boughtKey), String.valueOf(quantity), userKey);

        if (result == null || result == -1) {
            throw new BizException(CodeEnum.BIZ_ERROR, "秒杀未开始或库存未初始化");
        }
        if (result == 0) {
            throw new BizException(CodeEnum.BIZ_ERROR, "手慢了，库存不足");
        }
        if (result == -2) {
            throw new BizException(CodeEnum.BIZ_ERROR, "您已参与过该秒杀");
        }

        // 扣减成功 → 异步落库订单（不阻塞秒杀主链路）
        SeckillResultVO vo = new SeckillResultVO();
        try {
            Long orderId = seckillAsyncService.createSeckillOrderAsync(userId, act, skuId, quantity).get(3, java.util.concurrent.TimeUnit.SECONDS);
            vo.setSuccess(true);
            vo.setOrderId(orderId);
            vo.setMessage("秒杀成功，订单已生成");
        } catch (Exception e) {
            vo.setSuccess(true);
            vo.setMessage("秒杀成功，订单生成中");
        }
        return vo;
    }

    @Override
    public Long saveActivity(SeckillActivitySaveDTO dto) {
        SeckillActivity act;
        if (dto.getId() == null) {
            act = new SeckillActivity();
        } else {
            act = activityMapper.selectById(dto.getId());
            Assert.notNull(act, "活动不存在");
        }
        act.setSpuId(dto.getSpuId());
        act.setSkuId(dto.getSkuId());
        act.setTitle(dto.getTitle());
        act.setPrice(dto.getPrice());
        act.setStock(dto.getStock());
        act.setSold(act.getSold() == null ? 0 : act.getSold());
        act.setStartTime(dto.getStartTime());
        act.setEndTime(dto.getEndTime());
        act.setStatus(1);
        if (act.getId() == null) {
            activityMapper.insert(act);
        } else {
            activityMapper.updateById(act);
        }
        // 保存后预热库存到 Redis
        publishStock(act.getId());
        return act.getId();
    }

    @Override
    public void publishStock(Long activityId) {
        SeckillActivity act = activityMapper.selectById(activityId);
        if (act == null) {
            return;
        }
        String stockKey = String.format(RedisKeyConstants.SECKILL_STOCK, activityId, act.getSkuId());
        long ttl = act.getEndTime() != null
                ? Duration.between(LocalDateTime.now(), act.getEndTime()).getSeconds()
                : 3600;
        redisUtil.set(stockKey, String.valueOf(act.getStock()), Math.max(ttl, 60));
    }

    private SeckillActivityVO toVO(SeckillActivity act) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setId(act.getId());
        vo.setTitle(act.getTitle());
        vo.setPrice(act.getPrice());
        vo.setStock(act.getStock());
        vo.setSold(act.getSold());
        vo.setStatus(act.getStatus());
        vo.setStartTime(act.getStartTime());
        vo.setEndTime(act.getEndTime());
        return vo;
    }
}
