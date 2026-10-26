package com.ecommerce.learn.module.cart.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.module.cart.dto.*;
import com.ecommerce.learn.module.cart.entity.CartItem;
import com.ecommerce.learn.module.cart.mapper.CartMapper;
import com.ecommerce.learn.module.cart.service.CartService;
import com.ecommerce.learn.module.product.entity.Sku;
import com.ecommerce.learn.module.product.entity.Spu;
import com.ecommerce.learn.module.product.mapper.SkuMapper;
import com.ecommerce.learn.module.product.mapper.SpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 购物车服务
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartMapper cartMapper;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, CartAddDTO dto) {
        Sku sku = skuMapper.selectById(dto.getSkuId());
        if (sku == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "SKU 不存在");
        }
        if (sku.getStock() == null || sku.getStock() < dto.getQuantity()) {
            throw new BizException(CodeEnum.BIZ_ERROR, "库存不足");
        }
        CartItem existing = cartMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, dto.getSkuId()));
        if (existing != null) {
            int newQty = existing.getQuantity() + dto.getQuantity();
            if (sku.getStock() < newQty) {
                throw new BizException(CodeEnum.BIZ_ERROR, "库存不足");
            }
            existing.setQuantity(newQty);
            cartMapper.updateById(existing);
            return;
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setSkuId(sku.getId());
        item.setSpuId(sku.getSpuId());
        item.setQuantity(dto.getQuantity());
        item.setChecked(1);
        cartMapper.insert(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, CartUpdateDTO dto) {
        CartItem item = getOwnedItem(userId, dto.getSkuId());
        Sku sku = skuMapper.selectById(dto.getSkuId());
        if (sku != null && sku.getStock() < dto.getQuantity()) {
            throw new BizException(CodeEnum.BIZ_ERROR, "库存不足");
        }
        item.setQuantity(dto.getQuantity());
        cartMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void check(Long userId, CartCheckDTO dto) {
        CartItem item = getOwnedItem(userId, dto.getSkuId());
        item.setChecked(dto.getChecked());
        cartMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long skuId) {
        cartMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(Long userId) {
        cartMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
    }

    @Override
    public List<CartVO> list(Long userId) {
        List<CartItem> items = cartMapper.selectList(
                Wrappers.<CartItem>lambdaQuery().eq(CartItem::getUserId, userId));
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> skuIds = items.stream().map(CartItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Sku> skuMap = skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));
        Set<Long> spuIds = skuMap.values().stream().map(Sku::getSpuId).collect(Collectors.toSet());
        Map<Long, Spu> spuMap = spuMapper.selectBatchIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, s -> s, (a, b) -> a));

        List<CartVO> result = new ArrayList<>();
        for (CartItem item : items) {
            Sku sku = skuMap.get(item.getSkuId());
            CartVO vo = new CartVO();
            vo.setId(item.getId());
            vo.setSkuId(item.getSkuId());
            vo.setSpuId(item.getSpuId());
            vo.setQuantity(item.getQuantity());
            vo.setChecked(item.getChecked());
            if (sku != null) {
                vo.setPrice(sku.getPrice());
                vo.setImage(sku.getImage());
                vo.setStock(sku.getStock());
                Spu spu = spuMap.get(sku.getSpuId());
                vo.setTitle(spu == null ? null : spu.getTitle());
            }
            result.add(vo);
        }
        return result;
    }

    private CartItem getOwnedItem(Long userId, Long skuId) {
        CartItem item = cartMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));
        if (item == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "购物车中无此商品");
        }
        return item;
    }
}
