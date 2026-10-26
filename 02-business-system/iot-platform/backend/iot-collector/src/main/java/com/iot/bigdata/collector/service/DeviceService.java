package com.iot.bigdata.collector.service;

import com.iot.bigdata.collector.dto.DevicePageQuery;
import com.iot.bigdata.collector.dto.DeviceSaveRequest;
import com.iot.bigdata.collector.entity.Device;
import com.iot.bigdata.collector.repository.DeviceRepository;
import com.iot.bigdata.collector.vo.DeviceVO;
import com.iot.bigdata.common.constant.IotConstants;
import com.iot.bigdata.common.enums.ResultCode;
import com.iot.bigdata.common.exception.BusinessException;
import com.iot.bigdata.common.result.PageResult;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 设备档案服务。提供设备的增删改查与分页检索，供接口层与看板使用。
 */
@Slf4j
@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * 新增或更新设备（按设备编码幂等）。
     */
    @Transactional
    public DeviceVO save(DeviceSaveRequest request) {
        Device device = deviceRepository.findByDeviceCode(request.getDeviceCode()).orElse(null);
        if (device == null) {
            device = new Device();
            device.setDeviceCode(request.getDeviceCode());
            device.setStatus("OFFLINE");
        }
        device.setDeviceName(request.getDeviceName());
        deviceNameOrType(device, request);
        return toVO(deviceRepository.save(device));
    }

    private void deviceNameOrType(Device device, DeviceSaveRequest request) {
        device.setDeviceType(request.getDeviceType());
        device.setProtocol(request.getProtocol());
        device.setLocation(request.getLocation());
    }

    /**
     * 分页查询设备。
     */
    public PageResult<DeviceVO> page(DevicePageQuery query) {
        long pageNum = query.getPageNum() == null ? IotConstants.DEFAULT_PAGE_NUM : query.getPageNum();
        long pageSize = query.getPageSize() == null ? IotConstants.DEFAULT_PAGE_SIZE : query.getPageSize();
        Pageable pageable = PageRequest.of((int) (pageNum - 1), (int) pageSize);

        Specification<Device> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.getDeviceCode())) {
                predicates.add(cb.like(root.get("deviceCode"), "%" + query.getDeviceCode() + "%"));
            }
            if (StringUtils.hasText(query.getDeviceType())) {
                predicates.add(cb.equal(root.get("deviceType"), query.getDeviceType()));
            }
            if (StringUtils.hasText(query.getStatus())) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            predicates.add(cb.equal(root.get("deleted"), IotConstants.DELETED_NO));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Device> page = deviceRepository.findAll(spec, pageable);
        List<DeviceVO> records = page.getContent().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotalElements(), pageNum, pageSize);
    }

    public DeviceVO get(String deviceCode) {
        Device device = deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "设备不存在: " + deviceCode));
        return toVO(device);
    }

    public List<DeviceVO> listAll() {
        return deviceRepository.findAll().stream()
                .filter(d -> Objects.equals(d.getDeleted(), IotConstants.DELETED_NO))
                .map(this::toVO)
                .toList();
    }

    /**
     * 逻辑删除设备。
     */
    @Transactional
    public void delete(Long id) {
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "设备不存在: " + id));
        device.setDeleted(IotConstants.DELETED_YES);
        deviceRepository.save(device);
    }

    /** 实体转 VO。 */
    public DeviceVO toVO(Device device) {
        DeviceVO vo = new DeviceVO();
        BeanUtils.copyProperties(device, vo);
        return vo;
    }
}
