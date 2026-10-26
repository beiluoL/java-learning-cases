package com.ecommerce.learn.module.product.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.product.dto.SpuSaveDTO;
import com.ecommerce.learn.module.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台商品管理（需 ADMIN 角色，由拦截器校验）
 */
@Tag(name = "后台-商品管理")
@RestController
@RequestMapping("/api/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @Operation(summary = "新增/更新商品（SPU + SKU）")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody SpuSaveDTO dto) {
        return Result.success(productService.saveSpu(dto));
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        productService.removeSpu(id);
        return Result.success();
    }
}
