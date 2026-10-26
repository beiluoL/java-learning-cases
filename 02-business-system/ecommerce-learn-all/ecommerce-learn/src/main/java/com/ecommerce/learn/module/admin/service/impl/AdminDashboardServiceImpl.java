package com.ecommerce.learn.module.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.util.RedisUtil;
import com.ecommerce.learn.module.admin.dto.AdminStatsVO;
import com.ecommerce.learn.module.admin.service.AdminDashboardService;
import com.ecommerce.learn.module.order.constant.OrderStatus;
import com.ecommerce.learn.module.order.entity.Order;
import com.ecommerce.learn.module.order.mapper.OrderMapper;
import com.ecommerce.learn.module.product.entity.Spu;
import com.ecommerce.learn.module.product.mapper.SpuMapper;
import com.ecommerce.learn.module.seckill.entity.SeckillActivity;
import com.ecommerce.learn.module.seckill.mapper.SeckillActivityMapper;
import com.ecommerce.learn.module.user.entity.User;
import com.ecommerce.learn.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台看板：聚合各维度统计（结果可缓存以扛高并发查询）
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserMapper userMapper;
    private final SpuMapper spuMapper;
    private final OrderMapper orderMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final RedisUtil redisUtil;

    @Override
    public AdminStatsVO stats() {
        AdminStatsVO vo = new AdminStatsVO();
        vo.setUserCount(nullSafe(userMapper.selectCount(Wrappers.emptyWrapper())));
        vo.setProductCount(nullSafe(spuMapper.selectCount(Wrappers.emptyWrapper())));
        vo.setOrderCount(nullSafe(orderMapper.selectCount(Wrappers.emptyWrapper())));
        vo.setSeckillActivityCount(nullSafe(seckillAuditCount()));

        // 已支付（含后续状态）订单数
        Long paid = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .in(Order::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.FINISHED));
        vo.setPaidOrderCount(paid == null ? 0 : paid);

        // 销售额 = 已支付订单实付金额之和
        List<Order> paidOrders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .in(Order::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.FINISHED));
        BigDecimal sales = paidOrders.stream()
                .map(o -> o.getPayAmount() == null ? BigDecimal.ZERO : o.getPayAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTotalSales(sales);

        // 今日订单
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long today = orderMapper.selectCount(Wrappers.<Order>lambdaQuery().ge(Order::getCreatedAt, todayStart));
        vo.setTodayOrderCount(today == null ? 0 : today);

        return vo;
    }

    private long seckillAuditCount() {
        return nullSafe(seckillActivityMapper.selectCount(Wrappers.emptyWrapper()));
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
