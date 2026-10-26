package com.ecommerce.learn.module.product.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.product.dto.CategorySaveDTO;
import com.ecommerce.learn.module.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台分类管理
 */
@Tag(name = "后台-分类管理")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "新增分类")
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody CategorySaveDTO dto) {
        categoryService.save(dto);
        return Result.success();
    }

    @Operation(summary = "更新分类")
    @PutMapping("/save")
    public Result<Void> update(@Valid @RequestBody CategorySaveDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        categoryService.remove(id);
        return Result.success();
    }
}
