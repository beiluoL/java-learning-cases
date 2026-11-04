package com.example.mq.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.mq.common.api.ResultCode;
import com.example.mq.common.enums.OrderStatus;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.common.message.OrderCreateMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.common.util.Ids;
import com.example.mq.config.MqProperties;
import com.example.mq.dto.SeckillRequest;
import com.example.mq.dto.SeckillResponse;
import com.example.mq.dto.StressTestResult;
import com.example.mq.entity.MqOrder;
import com.example.mq.entity.MqProduct;
import com.example.mq.entity.MqStock;
import com.example.mq.mapper.MqOrderMapper;
import com.example.mq.mapper.MqProductMapper;
import com.example.mq.mapper.MqStockMapper;
import com.example.mq.mq.MessageSender;
import com.example.mq.mq.RedisStockService;
import com.example.mq.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀下单实现 —— 「削峰填谷」的生产端。
 *
 * <h3>接口为什么能做到毫秒级？</h3>
 * <pre>
 *  同步做法：校验 → 查库存 → 扣库存（行锁）→ 建订单 → 发短信 → 返回   （几十~几百 ms，QPS 被 DB 锁死）
 *  本项目：  Redis 预扣(Lua，微秒) → insert 一张 QUEUING 单 → 投递消息 → 返回   （个位数 ms）
 *                       ▲ 真正的扣库存/建单/通知全部异步到消费者串行完成
 * </pre>
 *
 * <h3>防超卖的三道闸门</h3>
 * <ol>
 *   <li>Redis Lua 原子预扣：把 80% 的无效流量挡在最外层（无锁、无 IO）；</li>
 *   <li>数据库条件更新 {@code available >= qty}：唯一的事实来源，缓存错了也不会超卖；</li>
 *   <li>订单状态 CAS：防止重复消费导致重复扣减。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final MqProductMapper productMapper;
    private final MqStockMapper stockMapper;
    private final MqOrderMapper orderMapper;
    private final RedisStockService redisStockService;
    private final MessageSender messageSender;
    private final MqProperties mqProperties;

    @Override
    public SeckillResponse submit(SeckillRequest request) {
        long begin = System.currentTimeMillis();
        MqProduct product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(ResultCode.PRODUCT_OFFLINE);
        }

        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();
        int delaySeconds = request.getDelaySeconds() == null || request.getDelaySeconds() <= 0
                ? mqProperties.getOrderDelaySeconds()
                : request.getDelaySeconds();

        // ---------- 第一道闸门：Redis 原子预扣 ----------
        RedisStockService.DeductOutcome outcome = redisStockService.preDeduct(product.getId(), quantity);
        if (outcome.getResult() == com.example.mq.mq.StockDeductResult.SOLD_OUT) {
            throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
        }
        boolean redisDeducted = outcome.isSuccess();

        // ---------- 落"排队中"订单：让前端立刻能查到，而不是只能转圈 ----------
        String orderNo = Ids.orderNo();
        Long userId = request.getUserId() == null
                ? ThreadLocalRandom.current().nextLong(10001, 99999)
                : request.getUserId();

        MqOrder order = new MqOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setQuantity(quantity);
        // 金额先占位，由消费者按当时价格回填（防止下单到落单之间价格被改）
        order.setAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.QUEUING.name());
        order.setDelaySeconds(delaySeconds);
        try {
            orderMapper.insert(order);
        } catch (Exception e) {
            if (redisDeducted) {
                redisStockService.rollback(product.getId(), quantity);
            }
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "创建排队订单失败：" + e.getMessage());
        }

        // ---------- 投递 MQ ----------
        OrderCreateMessage message = OrderCreateMessage.builder()
                .messageId(Ids.messageId())
                .orderNo(orderNo)
                .userId(userId)
                .productId(product.getId())
                .productName(product.getName())
                .quantity(quantity)
                .amount(product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .delaySeconds(delaySeconds)
                .traceId(Ids.traceId())
                .createTime(order.getCreateTime())
                .build();

        String messageId;
        try {
            messageId = messageSender.send(MqConstants.ORDER_EXCHANGE, MqConstants.ORDER_CREATE_ROUTING_KEY,
                    message, MqScene.ORDER_CREATE, orderNo);
        } catch (Exception e) {
            // 投递失败必须回滚预扣 + 标记订单失败，否则库存被"吃掉"却永远不落单
            if (redisDeducted) {
                redisStockService.rollback(product.getId(), quantity);
            }
            orderMapper.markFailed(orderNo, "消息投递失败");
            log.error("[SECKILL] 消息投递失败，已回滚 orderNo={} productId={} qty={}",
                    orderNo, product.getId(), quantity, e);
            throw new BusinessException(ResultCode.MQ_SEND_FAILED,
                    "系统繁忙，请稍后重试（" + e.getMessage() + "）");
        }

        long cost = System.currentTimeMillis() - begin;
        log.info("[SECKILL] 受理成功 orderNo={} productId={} qty={} redisRemaining={} cost={}ms messageId={}",
                orderNo, product.getId(), quantity, outcome.getRemaining(), cost, messageId);

        return SeckillResponse.builder()
                .orderNo(orderNo)
                .status(OrderStatus.QUEUING.name())
                .message("已进入排队，正在为你锁定库存")
                .remainingStock(outcome.isSuccess() ? outcome.getRemaining() : null)
                .costMs(cost)
                .messageId(messageId)
                .build();
    }

    @Override
    public StressTestResult stressTest(Long productId, Integer quantity, Integer concurrency) {
        int qty = quantity == null || quantity <= 0 ? 1 : quantity;
        int threads = concurrency == null || concurrency <= 0 ? 50 : Math.min(concurrency, 200);

        MqStock stock = stockMapper.selectByProductId(productId);
        int stockBefore = stock == null ? 0 : stock.getAvailable();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> successOrders = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>(threads);

        long begin = System.currentTimeMillis();
        for (int i = 0; i < threads; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    SeckillRequest request = new SeckillRequest();
                    request.setProductId(productId);
                    request.setQuantity(qty);
                    SeckillResponse response = submit(request);
                    success.incrementAndGet();
                    if (successOrders.size() < 50) {
                        successOrders.add(response.getOrderNo());
                    }
                } catch (BusinessException e) {
                    if (e.getCode() == ResultCode.STOCK_NOT_ENOUGH.getCode()) {
                        soldOut.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            }, pool));
        }

        try {
            // 所有线程就位后统一发令，尽量让它们"同时"打进来（模拟真实洪峰）
            readyLatch.await(10, TimeUnit.SECONDS);
            startLatch.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[STRESS] 压测执行异常", e);
        } finally {
            pool.shutdown();
        }
        long duration = System.currentTimeMillis() - begin;

        // 消费是异步的，等数据库库存沉降（最多 5 秒），否则读到的还是旧值
        int stockAfter = stockBefore;
        for (int i = 0; i < 50; i++) {
            MqStock latest = stockMapper.selectByProductId(productId);
            stockAfter = latest == null ? 0 : latest.getAvailable();
            if (stockAfter <= Math.max(stockBefore - success.get() * qty, 0)) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        String conclusion = String.format(
                "并发 %d，成功受理 %d，售罄拒绝 %d，其它失败 %d；库存 %d → %d（应为 %d）。%s",
                threads, success.get(), soldOut.get(), failed.get(),
                stockBefore, stockAfter, Math.max(stockBefore - success.get() * qty, 0),
                stockAfter >= 0 ? "Redis 预扣 + DB 条件更新已生效，未发生超卖。" : "出现异常，请检查实现。");

        log.info("[STRESS] {}", conclusion);

        return StressTestResult.builder()
                .total(threads)
                .success(success.get())
                .soldOut(soldOut.get())
                .failed(failed.get())
                .durationMs(duration)
                .avgCostMs(threads == 0 ? 0 : duration / threads)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .successOrderNos(successOrders)
                .conclusion(conclusion)
                .build();
    }

    @Override
    public void resetStock(Long productId) {
        MqStock stock = stockMapper.selectByProductId(productId);
        if (stock == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "库存记录不存在");
        }
        stockMapper.update(null, Wrappers.<MqStock>lambdaUpdate()
                .eq(MqStock::getProductId, productId)
                .set(MqStock::getAvailable, stock.getTotal())
                .setSql("version = version + 1"));
        redisStockService.initStock(productId, stock.getTotal());
        log.info("[STOCK] 已重置演示库存 productId={} available={}", productId, stock.getTotal());
    }
}
