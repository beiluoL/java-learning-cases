package com.ecommerce.learn.module.product.service;

import com.ecommerce.learn.common.result.PageResult;
import com.ecommerce.learn.module.product.dto.*;

public interface ProductService {

    /** 商品检索（分页 + 关键词/分类/品牌 + 排序） */
    PageResult<SpuSummaryVO> search(ProductQuery query);

    /** SPU 详情（含 SKU，带缓存） */
    SpuVO detail(Long spuId);

    /** 后台保存/更新 SPU 及其 SKU */
    Long saveSpu(SpuSaveDTO dto);

    /** 后台删除 SPU（逻辑删除，级联下架 SKU） */
    void removeSpu(Long spuId);
}
