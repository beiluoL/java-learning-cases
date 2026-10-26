package com.ecommerce.learn.module.product.service;

import com.ecommerce.learn.module.product.dto.CategorySaveDTO;
import com.ecommerce.learn.module.product.dto.CategoryVO;
import com.ecommerce.learn.module.product.entity.Category;

import java.util.List;

public interface CategoryService {

    /** 分类树（带缓存） */
    List<CategoryVO> tree();

    List<Category> listAll();

    void save(CategorySaveDTO dto);

    void update(CategorySaveDTO dto);

    void remove(Long id);
}
