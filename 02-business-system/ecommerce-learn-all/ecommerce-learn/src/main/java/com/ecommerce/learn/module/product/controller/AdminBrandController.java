package com.ecommerce.learn.module.product.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.product.dto.BrandSaveDTO;
import com.ecommerce.learn.module.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台品牌管理
 */
@Tag(name = "后台-品牌管理")
@RestController
@RequestMapping("/api/admin/brand")
@RequiredArgsConstructor
public class AdminBrandController {

    private final BrandService brandService;

    @Operation(summary = "新增/更新品牌")
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody BrandSaveDTO dto) {
        brandService.save(dto);
        return Result.success();
    }

    @Operation(summary = "删除品牌")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        brandService.remove(id);
        return Result.success();
    }
}
