package com.example.redislab.modules.cache;

import com.example.redislab.common.R;
import com.example.redislab.core.RedisCacheable;
import com.example.redislab.entity.Product;
import com.example.redislab.service.ProductService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 场景1：缓存加速。
 * GET  /api/cache/product/{id}  读（Cache-Aside，命中缓存则不查 DB）
 * PUT  /api/cache/product/{id}  更新（更新 DB + 删缓存）
 * DEL  /api/cache/product/{id}  删缓存
 * GET  /api/cache/demo          演示 @RedisCacheable 自定义注解
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final ProductService productService;

    public CacheController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/product/{id}")
    public R<Product> get(@PathVariable Long id) {
        return R.ok(productService.getByIdWithCache(id));
    }

    @PutMapping("/product/{id}")
    public R<Product> update(@PathVariable Long id,
                            @RequestParam String name,
                            @RequestParam BigDecimal price) {
        return R.ok(productService.update(id, name, price));
    }

    @DeleteMapping("/product/{id}")
    public R<Void> delete(@PathVariable Long id) {
        productService.deleteCache(id);
        return R.ok();
    }

    /** 自定义注解演示：连续调用两次，方法体只执行一次（第二次走缓存） */
    @RedisCacheable(key = "cache:demo:fixed", ttl = 60)
    @GetMapping("/demo")
    public R<String> demo() {
        return R.ok("这是被 @RedisCacheable 缓存的方法返回值（方法体真实只执行一次）");
    }
}
