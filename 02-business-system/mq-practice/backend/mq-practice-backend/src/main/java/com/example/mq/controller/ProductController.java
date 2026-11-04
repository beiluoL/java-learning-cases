package com.example.mq.controller;

import com.example.mq.common.api.Result;
import com.example.mq.dto.ProductChangeRequest;
import com.example.mq.dto.ProductChangeResult;
import com.example.mq.dto.ProductView;
import com.example.mq.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【场景三】商品管理：每次变更都会向 topic 交换机发布一条事件，
 * 由「缓存同步」与「索引同步」两个消费者并行处理。
 */
@Tag(name = "场景三：商品变更发布订阅")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "商品列表")
    @GetMapping
    public Result<List<ProductView>> list() {
        return Result.ok(productService.list());
    }

    @Operation(summary = "商品三视角快照：数据库 / Redis 缓存 / 检索索引")
    @GetMapping("/{id}")
    public Result<ProductView> snapshot(@PathVariable Long id) {
        return Result.ok(productService.snapshot(id));
    }

    @Operation(summary = "变更商品（上架/下架/改价）并发布 MQ 事件")
    @PostMapping("/{id}/change")
    public Result<ProductChangeResult> change(@PathVariable Long id,
                                              @Valid @RequestBody ProductChangeRequest request) {
        return Result.ok(productService.change(id, request));
    }
}
