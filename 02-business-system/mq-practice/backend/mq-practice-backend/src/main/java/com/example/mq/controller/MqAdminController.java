package com.example.mq.controller;

import com.example.mq.common.api.Result;
import com.example.mq.dto.admin.*;
import com.example.mq.service.MqAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RabbitMQ 运维台接口（Broker 侧视角）。
 *
 * <p>与 {@link MqMonitorController}（业务侧：消息账本 / 幂等 / 死信）互补：
 * 这里看"消息在 broker 里长什么样、谁在消费、绑定对不对"。
 *
 * <h3>安全约定</h3>
 * <ul>
 *   <li>所有请求都由服务端持凭据转发，浏览器永远拿不到 RabbitMQ 账号密码；</li>
 *   <li>只读接口不校验口令，写操作（清空 / 投递）必须带 {@code X-MQ-Admin-Token}；</li>
 *   <li>写操作在服务实现里还有二次约束（开关 + 队列前缀白名单），接口层不做业务判断。</li>
 * </ul>
 */
@Tag(name = "RabbitMQ 运维台（Broker 侧）")
@RestController
@RequestMapping("/api/mq/admin")
@RequiredArgsConstructor
public class MqAdminController {

    private final MqAdminService adminService;

    @Operation(summary = "连接状态与集群概览")
    @GetMapping("/status")
    public Result<AdminStatusView> status() {
        return Result.ok(adminService.status());
    }

    @Operation(summary = "队列列表（含就绪/未确认/速率/TTL）")
    @GetMapping("/queues")
    public Result<List<AdminQueueView>> queues() {
        return Result.ok(adminService.queues());
    }

    @Operation(summary = "交换机列表")
    @GetMapping("/exchanges")
    public Result<List<AdminExchangeView>> exchanges() {
        return Result.ok(adminService.exchanges());
    }

    @Operation(summary = "绑定关系")
    @GetMapping("/bindings")
    public Result<List<AdminBindingView>> bindings() {
        return Result.ok(adminService.bindings());
    }

    @Operation(summary = "消费者列表（prefetch / ack 模式 / 活跃状态）")
    @GetMapping("/consumers")
    public Result<List<AdminConsumerView>> consumers() {
        return Result.ok(adminService.consumers());
    }

    @Operation(summary = "连接列表")
    @GetMapping("/connections")
    public Result<List<AdminConnectionView>> connections() {
        return Result.ok(adminService.connections());
    }

    @Operation(summary = "查看队列消息（只读，读完放回，不会消费）")
    @PostMapping("/queues/{queue}/peek")
    public Result<List<PeekedMessageView>> peek(@PathVariable String queue,
                                                @RequestParam(defaultValue = "10") int count) {
        return Result.ok(adminService.peek(queue, count));
    }

    @Operation(summary = "清空队列（写操作，需 X-MQ-Admin-Token）")
    @PostMapping("/queues/{queue}/purge")
    public Result<Integer> purge(@PathVariable String queue,
                                 @RequestHeader(value = "X-MQ-Admin-Token", required = false) String token) {
        int purged = adminService.purge(queue, token);
        return Result.ok(purged, "已清空 " + purged + " 条消息");
    }

    @Operation(summary = "投递测试消息（写操作，需 X-MQ-Admin-Token）")
    @PostMapping("/publish")
    public Result<PublishResultView> publish(@Valid @RequestBody PublishMessageRequest request,
                                             @RequestHeader(value = "X-MQ-Admin-Token", required = false) String token) {
        return Result.ok(adminService.publish(request, token));
    }
}
