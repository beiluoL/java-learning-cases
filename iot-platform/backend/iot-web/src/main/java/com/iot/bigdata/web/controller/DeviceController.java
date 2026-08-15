package com.iot.bigdata.web.controller;

import com.iot.bigdata.analysis.service.AnalysisFacade;
import com.iot.bigdata.collector.dto.DevicePageQuery;
import com.iot.bigdata.collector.dto.DeviceSaveRequest;
import com.iot.bigdata.collector.service.DeviceService;
import com.iot.bigdata.common.result.ApiResult;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备档案接口。提供设备 CRUD 与实时快照查询。
 */
@Slf4j
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final AnalysisFacade analysisFacade;

    public DeviceController(DeviceService deviceService, AnalysisFacade analysisFacade) {
        this.deviceService = deviceService;
        this.analysisFacade = analysisFacade;
    }

    @GetMapping
    public ApiResult<?> page(@RequestParam(required = false) String deviceCode,
                             @RequestParam(required = false) String deviceType,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "1") Long pageNum,
                             @RequestParam(defaultValue = "20") Long pageSize) {
        DevicePageQuery query = new DevicePageQuery();
        query.setDeviceCode(deviceCode);
        query.setDeviceType(deviceType);
        query.setStatus(status);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return ApiResult.success(deviceService.page(query));
    }

    @PostMapping
    public ApiResult<?> save(@Valid @RequestBody DeviceSaveRequest request) {
        return ApiResult.success(deviceService.save(request));
    }

    @GetMapping("/{deviceCode}")
    public ApiResult<?> get(@PathVariable String deviceCode) {
        return ApiResult.success(deviceService.get(deviceCode));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ApiResult.success();
    }

    @GetMapping("/realtime")
    public ApiResult<?> realtime(@RequestParam(required = false) String deviceCode) {
        if (deviceCode == null || deviceCode.isEmpty()) {
            return ApiResult.success(analysisFacade.realtimeSnapshots());
        }
        return ApiResult.success(analysisFacade.snapshotsByDevice(deviceCode));
    }
}
