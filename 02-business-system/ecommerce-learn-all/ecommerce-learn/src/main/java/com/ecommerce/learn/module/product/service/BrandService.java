package com.ecommerce.learn.module.product.service;

import com.ecommerce.learn.module.product.dto.BrandSaveDTO;
import com.ecommerce.learn.module.product.dto.BrandVO;

import java.util.List;

public interface BrandService {

    List<BrandVO> listAll();

    void save(BrandSaveDTO dto);

    void remove(Long id);
}
