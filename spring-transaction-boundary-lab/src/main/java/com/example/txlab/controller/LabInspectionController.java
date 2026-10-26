package com.example.txlab.controller;

import com.example.txlab.repository.AuditRepository;
import com.example.txlab.repository.OrderRepository;
import com.example.txlab.support.LabDataResetter;
import com.example.txlab.trace.TraceRecorder;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 观察接口：把"执行轨迹"和"数据库真实状态"直接暴露出来，方便用 curl 取证。
 *
 * <ul>
 *   <li>{@code GET  /api/lab/trace}：上一次实验的真实执行轨迹（按序号排列）</li>
 *   <li>{@code GET  /api/lab/db}：当前两张表的真实数据</li>
 *   <li>{@code POST /api/lab/reset}：重置数据到 PENDING + 清空审计表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/lab")
public class LabInspectionController {

    private final TraceRecorder trace;
    private final OrderRepository orderRepository;
    private final AuditRepository auditRepository;
    private final LabDataResetter resetter;

    public LabInspectionController(TraceRecorder trace,
                                   OrderRepository orderRepository,
                                   AuditRepository auditRepository,
                                   LabDataResetter resetter) {
        this.trace = trace;
        this.orderRepository = orderRepository;
        this.auditRepository = auditRepository;
        this.resetter = resetter;
    }

    /** 查看上一次实验的执行轨迹 */
    @GetMapping(value = "/trace", produces = MediaType.TEXT_PLAIN_VALUE)
    public String trace() {
        return trace.render();
    }

    /** 查看数据库真实状态 */
    @GetMapping("/db")
    public Map<String, Object> db() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orders", orderRepository.findAll());
        result.put("ordersCount", orderRepository.countAll());
        result.put("auditLogs", auditRepository.findAll());
        result.put("auditLogCount", auditRepository.countAll());
        return result;
    }

    /** 重置实验数据（清空审计表 + 三个订单恢复 PENDING） */
    @PostMapping(value = "/reset", produces = MediaType.TEXT_PLAIN_VALUE)
    public String reset() {
        return resetter.reset();
    }
}
