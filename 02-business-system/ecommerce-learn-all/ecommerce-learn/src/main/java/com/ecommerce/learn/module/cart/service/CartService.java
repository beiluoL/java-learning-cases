package com.ecommerce.learn.module.cart.service;

import com.ecommerce.learn.module.cart.dto.*;
import com.ecommerce.learn.module.cart.entity.CartItem;

import java.util.List;

public interface CartService {

    void add(Long userId, CartAddDTO dto);

    void updateQuantity(Long userId, CartUpdateDTO dto);

    void check(Long userId, CartCheckDTO dto);

    void remove(Long userId, Long skuId);

    void clear(Long userId);

    List<CartVO> list(Long userId);
}
