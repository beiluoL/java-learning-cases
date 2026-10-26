package com.ecommerce.learn.module.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.learn.common.constants.RedisKeyConstants;
import com.ecommerce.learn.common.exception.Assert;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.common.result.PageResult;
import com.ecommerce.learn.common.util.OrderNoGenerator;
import com.ecommerce.learn.common.util.RedisUtil;
import com.ecommerce.learn.module.cart.entity.CartItem;
import com.ecommerce.learn.module.cart.mapper.CartMapper;
import com.ecommerce.learn.module.order.constant.OrderStatus;
import com.ecommerce.learn.module.order.dto.*;
import com.ecommerce.learn.module.order.entity.Order;
import com.ecommerce.learn.module.order.entity.OrderItem;
import com.ecommerce.learn.module.order.mapper.OrderItemMapper;
import com.ecommerce.learn.module.order.mapper.OrderMapper;
import com.ecommerce.learn.module.order.service.OrderService;
import com.ecommerce.learn.module.pay.dto.PayInfoVO;
import com.ecommerce.learn.module.pay.service.PayService;
import com.ecommerce.learn.module.product.entity.Sku;
import com.ecommerce.learn.module.product.entity.Spu;
import com.ecommerce.learn.module.product.mapper.SkuMapper;
import com.ecommerce.learn.module.product.mapper.SpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务：创建、支付、取消、发货、查询
 * <p>
 * 库存策略：下单时通过「数据库条件更新」原子预占库存（stock -= qty, locked_stock += qty），
 * 支付成功后把 locked_stock 转为销量；取消时回滚预占。该方式在单实例下即可防超卖。
 * 分布式事务（跨服务）演示见秒杀模块 {@code SeckillService} 中的 {@code @GlobalTransactional} 用法（需启用 Seata）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final CartMapper cartMapper;
    private final PayService payService;
    private final RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO createOrder(Long userId, CreateOrderDTO dto) {
        List<OrderItemDTO> items;
        if (Boolean.TRUE.equals(dto.getUseCart())) {
            List<CartItem> cartItems = cartMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                    .eq(CartItem::getUserId, userId).eq(CartItem::getChecked, 1));
            items = cartItems.stream().map(c -> {
                OrderItemDTO item = new OrderItemDTO();
                item.setSkuId(c.getSkuId());
                item.setQuantity(c.getQuantity());
                return item;
            }).collect(Collectors.toList());
        } else {
            items = dto.getItems();
        }
        Assert.isTrue(items != null && !items.isEmpty(), "订单商品不能为空");

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemDTO it : items) {
            Sku sku = skuMapper.selectById(it.getSkuId());
            Assert.notNull(sku, "SKU 不存在");
            // 原子预占库存（DB 层条件更新，避免并发超卖）
            int rows = skuMapper.update(null, Wrappers.<Sku>lambdaUpdate()
                    .eq(Sku::getId, sku.getId())
                    .ge(Sku::getStock, it.getQuantity())
                    .setSql("stock = stock - " + it.getQuantity())
                    .setSql("locked_stock = locked_stock + " + it.getQuantity()));
            if (rows == 0) {
                throw new BizException(CodeEnum.BIZ_ERROR, "商品[" + (sku.getSkuCode() == null ? sku.getId() : sku.getSkuCode()) + "]库存不足");
            }
            Sku fresh = skuMapper.selectById(it.getSkuId());
            Spu spu = spuMapper.selectById(fresh.getSpuId());
            OrderItem oi = new OrderItem();
            oi.setSkuId(fresh.getId());
            oi.setSpuId(fresh.getSpuId());
            oi.setSkuName(spu == null ? null : spu.getTitle());
            oi.setImage(fresh.getImage());
            oi.setSpecs(fresh.getSpecs());
            oi.setPrice(fresh.getPrice());
            oi.setQuantity(it.getQuantity());
            oi.setTotalAmount(fresh.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())));
            orderItems.add(oi);
            total = total.add(oi.getTotalAmount());
        }

        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setRemark(dto.getRemark());
        orderMapper.insert(order);
        Long orderId = order.getId();
        for (OrderItem oi : orderItems) {
            oi.setOrderId(orderId);
            orderItemMapper.insert(oi);
        }
        if (Boolean.TRUE.equals(dto.getUseCart())) {
            cartMapper.delete(Wrappers.<CartItem>lambdaQuery()
                    .eq(CartItem::getUserId, userId).eq(CartItem::getChecked, 1));
        }
        return new OrderCreateVO(orderId, order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayInfoVO pay(Long userId, Long orderId, String channel) {
        Order order = orderMapper.selectById(orderId);
        Assert.notNull(order, "订单不存在");
        if (!order.getUserId().equals(userId)) {
            throw new BizException(CodeEnum.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BizException(CodeEnum.BIZ_ERROR, "订单状态不可支付");
        }
        order.setPayType(channel);
        orderMapper.updateById(order);
        return payService.createPayment(order, channel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPaid(String orderNo, String tradeNo) {
        Order order = orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            return; // 幂等：已处理则直接返回
        }
        order.setStatus(OrderStatus.PAID);
        order.setTradeNo(tradeNo);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 释放预占库存 → 转为销量
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        Set<Long> spuIds = new HashSet<>();
        for (OrderItem item : items) {
            skuMapper.update(null, Wrappers.<Sku>lambdaUpdate()
                    .eq(Sku::getId, item.getSkuId())
                    .setSql("locked_stock = locked_stock - " + item.getQuantity())
                    .setSql("sales = sales + " + item.getQuantity()));
            spuIds.add(item.getSpuId());
        }
        spuIds.forEach(id -> redisUtil.delete(String.format(RedisKeyConstants.PRODUCT_DETAIL, id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        Assert.notNull(order, "订单不存在");
        if (!order.getUserId().equals(userId)) {
            throw new BizException(CodeEnum.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BizException(CodeEnum.BIZ_ERROR, "仅待支付订单可取消");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCloseTime(LocalDateTime.now());
        orderMapper.updateById(order);
        // 释放预占库存
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, orderId));
        for (OrderItem item : items) {
            skuMapper.update(null, Wrappers.<Sku>lambdaUpdate()
                    .eq(Sku::getId, item.getSkuId())
                    .setSql("stock = stock + " + item.getQuantity())
                    .setSql("locked_stock = locked_stock - " + item.getQuantity()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        Assert.notNull(order, "订单不存在");
        if (!order.getUserId().equals(userId)) {
            throw new BizException(CodeEnum.FORBIDDEN, "无权操作该订单");
        }
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new BizException(CodeEnum.BIZ_ERROR, "仅已发货订单可确认收货");
        }
        order.setStatus(OrderStatus.FINISHED);
        order.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public OrderVO detail(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        Assert.notNull(order, "订单不存在");
        if (!order.getUserId().equals(userId)) {
            throw new BizException(CodeEnum.FORBIDDEN, "无权查看该订单");
        }
        return buildOrderVO(order);
    }

    @Override
    public PageResult<OrderVO> myOrders(Long userId, long current, long size) {
        Page<Order> page = orderMapper.selectPage(new Page<>(current, size),
                Wrappers.<Order>lambdaQuery().eq(Order::getUserId, userId).orderByDesc(Order::getId));
        return buildPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long orderId, String logisticsCompany, String logisticsNo) {
        Order order = orderMapper.selectById(orderId);
        Assert.notNull(order, "订单不存在");
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BizException(CodeEnum.BIZ_ERROR, "仅已支付订单可发货");
        }
        order.setStatus(OrderStatus.SHIPPED);
        order.setLogisticsCompany(logisticsCompany);
        order.setLogisticsNo(logisticsNo);
        order.setShipTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    @Override
    public PageResult<OrderVO> adminList(long current, long size) {
        Page<Order> page = orderMapper.selectPage(new Page<>(current, size),
                Wrappers.<Order>lambdaQuery().orderByDesc(Order::getId));
        return buildPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSeckillOrder(Long userId, Long skuId, int quantity, BigDecimal price) {
        Sku sku = skuMapper.selectById(skuId);
        Assert.notNull(sku, "SKU 不存在");
        // DB 层二次兜底扣减（防止 Redis 与 DB 不一致导致的超卖）
        int rows = skuMapper.update(null, Wrappers.<Sku>lambdaUpdate()
                .eq(Sku::getId, skuId)
                .ge(Sku::getStock, quantity)
                .setSql("stock = stock - " + quantity)
                .setSql("sales = sales + " + quantity));
        if (rows == 0) {
            throw new BizException(CodeEnum.BIZ_ERROR, "库存不足");
        }
        Spu spu = spuMapper.selectById(sku.getSpuId());
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
        Order order = new Order();
        order.setOrderNo(OrderNoGenerator.next());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setStatus(OrderStatus.PAID);
        order.setPayType("seckill");
        order.setPayTime(LocalDateTime.now());
        orderMapper.insert(order);
        OrderItem oi = new OrderItem();
        oi.setOrderId(order.getId());
        oi.setSkuId(sku.getId());
        oi.setSpuId(sku.getSpuId());
        oi.setSkuName(spu == null ? null : spu.getTitle());
        oi.setImage(sku.getImage());
        oi.setSpecs(sku.getSpecs());
        oi.setPrice(price);
        oi.setQuantity(quantity);
        oi.setTotalAmount(total);
        orderItemMapper.insert(oi);
        redisUtil.delete(String.format(RedisKeyConstants.PRODUCT_DETAIL, sku.getSpuId()));
        return order.getId();
    }

    /* ----------------------- 内部工具 ----------------------- */

    private OrderVO buildOrderVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.desc(order.getStatus()));
        vo.setPayType(order.getPayType());
        vo.setTradeNo(order.getTradeNo());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setLogisticsCompany(order.getLogisticsCompany());
        vo.setLogisticsNo(order.getLogisticsNo());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setPayTime(order.getPayTime());
        vo.setShipTime(order.getShipTime());
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, order.getId()));
        vo.setItems(items.stream().map(this::toItemVO).collect(Collectors.toList()));
        return vo;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setSkuId(item.getSkuId());
        vo.setSpuId(item.getSpuId());
        vo.setSkuName(item.getSkuName());
        vo.setImage(item.getImage());
        vo.setSpecs(item.getSpecs());
        vo.setPrice(item.getPrice());
        vo.setQuantity(item.getQuantity());
        vo.setTotalAmount(item.getTotalAmount());
        return vo;
    }

    private PageResult<OrderVO> buildPage(Page<Order> page) {
        List<OrderVO> list = page.getRecords().stream()
                .map(o -> {
                    OrderVO vo = new OrderVO();
                    vo.setId(o.getId());
                    vo.setOrderNo(o.getOrderNo());
                    vo.setUserId(o.getUserId());
                    vo.setTotalAmount(o.getTotalAmount());
                    vo.setPayAmount(o.getPayAmount());
                    vo.setStatus(o.getStatus());
                    vo.setStatusDesc(OrderStatus.desc(o.getStatus()));
                    vo.setPayType(o.getPayType());
                    vo.setReceiverName(o.getReceiverName());
                    vo.setLogisticsCompany(o.getLogisticsCompany());
                    vo.setLogisticsNo(o.getLogisticsNo());
                    vo.setCreatedAt(o.getCreatedAt());
                    return vo;
                }).toList();
        return new PageResult<>(list, page.getTotal(), page.getSize(), page.getCurrent(), page.getPages());
    }
}
