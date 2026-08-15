package com.enterprise.erpcrm.module.system.controller;

import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysDictData;
import com.enterprise.erpcrm.module.system.entity.SysDictType;
import com.enterprise.erpcrm.module.system.service.SysDictDataService;
import com.enterprise.erpcrm.module.system.service.SysDictTypeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据字典控制器（类型 + 数据）。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictTypeService typeService;
    private final SysDictDataService dataService;

    @GetMapping("/type/list")
    @OperLog(title = "字典类型", businessType = BusinessType.QUERY)
    public Result<List<SysDictType>> typeList() {
        return Result.success(typeService.list());
    }

    @PostMapping("/type")
    @OperLog(title = "字典类型", businessType = BusinessType.INSERT)
    public Result<Void> addType(@RequestBody SysDictType type) {
        typeService.save(type);
        return Result.success();
    }

    @PutMapping("/type")
    @OperLog(title = "字典类型", businessType = BusinessType.UPDATE)
    public Result<Void> editType(@RequestBody SysDictType type) {
        typeService.updateById(type);
        return Result.success();
    }

    @DeleteMapping("/type/{ids}")
    @OperLog(title = "字典类型", businessType = BusinessType.DELETE)
    public Result<Void> removeType(@PathVariable List<Long> ids) {
        typeService.removeByIds(ids);
        return Result.success();
    }

    /** 按类型查询字典数据（前端下拉框用） */
    @GetMapping("/data/{dictType}")
    public Result<List<SysDictData>> dictData(@PathVariable String dictType) {
        return Result.success(dataService.selectByType(dictType));
    }

    @PostMapping("/data")
    @OperLog(title = "字典数据", businessType = BusinessType.INSERT)
    public Result<Void> addData(@RequestBody SysDictData data) {
        dataService.save(data);
        return Result.success();
    }

    @PutMapping("/data")
    @OperLog(title = "字典数据", businessType = BusinessType.UPDATE)
    public Result<Void> editData(@RequestBody SysDictData data) {
        dataService.updateById(data);
        return Result.success();
    }

    @DeleteMapping("/data/{ids}")
    @OperLog(title = "字典数据", businessType = BusinessType.DELETE)
    public Result<Void> removeData(@PathVariable List<Long> ids) {
        dataService.removeByIds(ids);
        return Result.success();
    }
}
