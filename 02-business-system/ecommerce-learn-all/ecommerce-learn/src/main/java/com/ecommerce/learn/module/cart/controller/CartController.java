package com.ecommerce.learn.module.cart.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.cart.dto.*;
import com.ecommerce.learn.module.cart.service.CartService;
import com.ecommerce.learn.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车接口（需登录）
 */
@Tag(name = "购物车")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "加入购物车")
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody CartAddDTO dto) {
        cartService.add(UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "修改数量")
    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@Valid @RequestBody CartUpdateDTO dto) {
        cartService.updateQuantity(UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "选中/取消")
    @PutMapping("/check")
    public Result<Void> check(@Valid @RequestBody CartCheckDTO dto) {
        cartService.check(UserContext.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除条目")
    @DeleteMapping("/{skuId}")
    public Result<Void> remove(@PathVariable Long skuId) {
        cartService.remove(UserContext.getUserId(), skuId);
        return Result.success();
    }

    @Operation(summary = "清空购物车")
    @DeleteMapping("/clear")
    public Result<Void> clear() {
        cartService.clear(UserContext.getUserId());
        return Result.success();
    }

    @Operation(summary = "购物车列表")
    @GetMapping("/list")
    public Result<List<CartVO>> list() {
        return Result.success(cartService.list(UserContext.getUserId()));
    }
}
