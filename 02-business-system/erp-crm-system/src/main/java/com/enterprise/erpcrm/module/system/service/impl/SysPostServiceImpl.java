package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.module.system.entity.SysPost;
import com.enterprise.erpcrm.module.system.mapper.SysPostMapper;
import com.enterprise.erpcrm.module.system.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 系统岗位服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {
}
