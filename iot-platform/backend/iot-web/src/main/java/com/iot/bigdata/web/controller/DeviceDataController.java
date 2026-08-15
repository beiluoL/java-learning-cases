package com.iot.bigdata.web.controller;

import com.iot.bigdata.collector.service.DeviceDataQueryService;
import com.iot.bigdata.collector.service.DeviceDataService;
import com.iot.bigdata.common.result.ApiResult;
import com.iot.bigdata.common.util.DateUtils;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 设备数据接口。提供时序数据范围查询、最新值查询与手动写入入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
public class DeviceDataController {

    private final DeviceDataQueryService dataQueryService;
    private final DeviceDataService deviceDataService;

    public DeviceDataController(DeviceDataQueryService dataQueryService, DeviceDataService deviceDataService) {
        this.dataQueryService = dataQueryService;
        this.deviceDataService = deviceDataService;
    }

    @GetMapping("/range")
    public ApiResult<?> range(@RequestParam String deviceCode,
                              @RequestParam String metric,
                              @RequestParam(required = false) String start,
                              @RequestParam(required = false) String end) {
        LocalDateTime endT = end == null ? LocalDateTime.now() : DateUtils.parse(end);
        LocalDateTime startT = start == null ? endT.minusHours(6) : DateUtils.parse(start);
        return ApiResult.success(dataQueryService.range(deviceCode, metric, startT, endT));
    }

    @GetMapping("/latest")
    public ApiResult<?> latest(@RequestParam String deviceCode, @RequestParam String metric) {
        return ApiResult.success(dataQueryService.latest(deviceCode, metric));
    }

    @PostMapping("/ingest")
    public ApiResult<?> ingest(@RequestBody DeviceDataMessage message) {
        deviceDataService.ingest(message);
        return ApiResult.success();
    }
}
