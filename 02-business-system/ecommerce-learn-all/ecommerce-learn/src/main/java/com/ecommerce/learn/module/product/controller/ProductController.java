package com.ecommerce.learn.module.product.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.product.dto.ProductQuery;
import com.ecommerce.learn.module.product.dto.SpuSummaryVO;
import com.ecommerce.learn.module.product.dto.SpuVO;
import com.ecommerce.learn.module.product.service.ProductService;
import com.ecommerce.learn.common.result.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商品公开接口
 */
@Tag(name = "商品检索")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品分页检索")
    @GetMapping
    public Result<PageResult<SpuSummaryVO>> search(ProductQuery query) {
        return Result.success(productService.search(query));
    }

    @Operation(summary = "商品详情（SPU + SKU）")
    @GetMapping("/{id}")
    public Result<SpuVO> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }
}
