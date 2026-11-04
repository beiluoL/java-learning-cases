package com.example.mq.controller;

import com.example.mq.common.api.PageResult;
import com.example.mq.common.api.Result;
import com.example.mq.common.mq.MqScene;
import com.example.mq.dto.ChaosConfig;
import com.example.mq.dto.DlqMessageView;
import com.example.mq.dto.MqOverviewView;
import com.example.mq.dto.QueueInfoView;
import com.example.mq.entity.MqIdempotentLog;
import com.example.mq.entity.MqMessageLog;
import com.example.mq.mq.ChaosSwitch;
import com.example.mq.service.MqMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MQ 控制台接口：看队列、看消息账本、看幂等记录、看/回放死信、注入故障。
 */
@Tag(name = "MQ 运维控制台")
@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class MqMonitorController {

    private final MqMonitorService monitorService;
    private final ChaosSwitch chaosSwitch;

    @Operation(summary = "总览：队列深度 + 消息统计 + 订单状态分布")
    @GetMapping("/overview")
    public Result<MqOverviewView> overview() {
        return Result.ok(monitorService.overview());
    }

    @Operation(summary = "队列列表")
    @GetMapping("/queues")
    public Result<List<QueueInfoView>> queues() {
        return Result.ok(monitorService.queues());
    }

    @Operation(summary = "消息发送账本（Publisher Confirm / Return 结果）")
    @GetMapping("/messages")
    public Result<PageResult<MqMessageLog>> messages(@RequestParam(required = false) String scene,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "10") long size) {
        return Result.ok(monitorService.messages(scene, status, page, size));
    }

    @Operation(summary = "消费幂等记录")
    @GetMapping("/idempotent")
    public Result<PageResult<MqIdempotentLog>> idempotent(@RequestParam(required = false) String scene,
                                                          @RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(monitorService.idempotentLogs(scene, page, size));
    }

    @Operation(summary = "查看死信（只读，不会消费掉）")
    @GetMapping("/dlq")
    public Result<List<DlqMessageView>> dlq(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(monitorService.peekDlq(limit));
    }

    @Operation(summary = "死信回放（按原始交换机/路由键重投）")
    @PostMapping("/dlq/replay")
    public Result<Integer> replay() {
        int count = monitorService.replayDlq();
        return Result.ok(count, "已回放 " + count + " 条死信");
    }

    @Operation(summary = "查看故障注入状态")
    @GetMapping("/chaos")
    public Result<Map<String, ChaosConfig>> chaos() {
        return Result.ok(chaosSwitch.snapshot());
    }

    @Operation(summary = "设置故障注入（用于观察重试→死信→回放全链路）")
    @PostMapping("/chaos")
    public Result<Map<String, ChaosConfig>> setChaos(@RequestParam String scene,
                                                     @RequestParam(defaultValue = "true") boolean enabled,
                                                     @RequestParam(defaultValue = "100") int failRate) {
        chaosSwitch.configure(MqScene.valueOf(scene), enabled, failRate);
        return Result.ok(chaosSwitch.snapshot(), "故障注入已更新");
    }
}
