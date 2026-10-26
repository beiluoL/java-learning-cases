package com.enterprise.erpcrm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.enterprise.erpcrm.module.system.entity.SysDictData;
import java.util.List;

/**
 * 字典数据服务接口。
 *
 * @author erp-crm-system
 */
public interface SysDictDataService extends IService<SysDictData> {

    /**
     * 按字典类型查询启用的字典数据（按 dictSort 升序）。
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<SysDictData> selectByType(String dictType);
}
