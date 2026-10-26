package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.system.entity.SysDictData;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 字典数据 Mapper。
 *
 * @author erp-crm-system
 */
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    /** 按字典类型查数据项（按排序） */
    @Select("SELECT * FROM sys_dict_data WHERE dict_type = #{dictType} AND status = 0 "
            + "AND deleted = 0 ORDER BY dict_sort ASC")
    List<SysDictData> selectByDictType(@Param("dictType") String dictType);
}
