package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.module.system.entity.SysDictType;
import com.enterprise.erpcrm.module.system.mapper.SysDictTypeMapper;
import com.enterprise.erpcrm.module.system.service.SysDictTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 字典类型服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeServiceImpl extends ServiceImpl<SysDictTypeMapper, SysDictType>
        implements SysDictTypeService {
}
