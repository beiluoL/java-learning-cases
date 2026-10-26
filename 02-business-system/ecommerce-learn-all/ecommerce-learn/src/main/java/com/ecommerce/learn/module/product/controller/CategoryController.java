package com.ecommerce.learn.module.product.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.product.dto.CategoryVO;
import com.ecommerce.learn.module.product.entity.Category;
import com.ecommerce.learn.module.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类公开接口
 */
@Tag(name = "商品分类")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类树")
    @GetMapping
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }

    @Operation(summary = "全部分类（平铺）")
    @GetMapping("/all")
    public Result<List<Category>> all() {
        return Result.success(categoryService.listAll());
    }
}
