package com.enterprise.erpcrm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.erpcrm.common.base.BasePageQuery;
import com.enterprise.erpcrm.common.result.PageResult;
import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysPost;
import com.enterprise.erpcrm.module.system.service.SysPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统岗位管理控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final SysPostService postService;

    @GetMapping("/list")
    @OperLog(title = "岗位管理", businessType = BusinessType.QUERY)
    public Result<PageResult<SysPost>> list(BasePageQuery query, SysPost post) {
        var page = postService.page(query.toPage(),
                new LambdaQueryWrapper<SysPost>().like(post.getPostName() != null, SysPost::getPostName, post.getPostName())
                        .orderByAsc(SysPost::getPostSort));
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/options")
    public Result<java.util.List<SysPost>> options() {
        return Result.success(postService.list());
    }

    @PostMapping
    @OperLog(title = "岗位管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody SysPost post) {
        postService.save(post);
        return Result.success();
    }

    @PutMapping
    @OperLog(title = "岗位管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody SysPost post) {
        postService.updateById(post);
        return Result.success();
    }

    @DeleteMapping("/{ids}")
    @OperLog(title = "岗位管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable java.util.List<Long> ids) {
        postService.removeByIds(ids);
        return Result.success();
    }
}
