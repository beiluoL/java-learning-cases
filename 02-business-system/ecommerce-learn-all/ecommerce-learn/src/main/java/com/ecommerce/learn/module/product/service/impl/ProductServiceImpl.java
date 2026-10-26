package com.ecommerce.learn.module.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.learn.common.constants.RedisKeyConstants;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.common.result.PageResult;
import com.ecommerce.learn.common.util.RedisUtil;
import com.ecommerce.learn.module.product.dto.*;
import com.ecommerce.learn.module.product.entity.*;
import com.ecommerce.learn.module.product.mapper.*;
import com.ecommerce.learn.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品服务：检索、详情缓存、后台保存
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final RedisUtil redisUtil;

    @Override
    public PageResult<SpuSummaryVO> search(ProductQuery q) {
        var wrapper = Wrappers.<Spu>lambdaQuery();
        wrapper.eq(q.getCategoryId() != null, Spu::getCategoryId, q.getCategoryId())
                .eq(q.getBrandId() != null, Spu::getBrandId, q.getBrandId())
                .like(StringUtils.hasText(q.getKeyword()), Spu::getTitle, q.getKeyword())
                .eq(Spu::getStatus, 1);
        if ("price_asc".equals(q.getSort())) {
            wrapper.orderByAsc(Spu::getId);
        } else {
            wrapper.orderByDesc(Spu::getId);
        }
        Page<Spu> page = spuMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), wrapper);

        List<Long> ids = page.getRecords().stream().map(Spu::getId).toList();
        Map<Long, List<Sku>> skuMap = ids.isEmpty() ? Map.of() :
                skuMapper.selectList(Wrappers.<Sku>lambdaQuery().in(Sku::getSpuId, ids))
                        .stream().collect(Collectors.groupingBy(Sku::getSpuId));
        Map<Long, String> catMap = categoryMapper.selectList(Wrappers.emptyWrapper())
                .stream().collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        Map<Long, String> brandMap = brandMapper.selectList(Wrappers.emptyWrapper())
                .stream().collect(Collectors.toMap(Brand::getId, Brand::getName, (a, b) -> a));

        List<SpuSummaryVO> list = page.getRecords().stream().map(spu -> {
            SpuSummaryVO vo = new SpuSummaryVO();
            vo.setId(spu.getId());
            vo.setTitle(spu.getTitle());
            vo.setMainImage(spu.getMainImage());
            vo.setStatus(spu.getStatus());
            vo.setCategoryName(catMap.get(spu.getCategoryId()));
            vo.setBrandName(brandMap.get(spu.getBrandId()));
            List<Sku> skus = skuMap.getOrDefault(spu.getId(), Collections.emptyList());
            fillPriceAndSales(vo, skus);
            return vo;
        }).toList();

        return new PageResult<>(list, page.getTotal(), page.getSize(), page.getCurrent(), page.getPages());
    }

    @Override
    public SpuVO detail(Long spuId) {
        String key = String.format(RedisKeyConstants.PRODUCT_DETAIL, spuId);
        SpuVO cached = redisUtil.getObject(key, SpuVO.class);
        if (cached != null) {
            return cached;
        }
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "商品不存在");
        }
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery().eq(Sku::getSpuId, spuId));
        SpuVO vo = buildSpuVO(spu, skus);
        redisUtil.setObject(key, vo, RedisKeyConstants.PRODUCT_DETAIL_TTL);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSpu(SpuSaveDTO dto) {
        Spu spu;
        if (dto.getId() == null) {
            spu = new Spu();
        } else {
            spu = spuMapper.selectById(dto.getId());
            if (spu == null) {
                throw new BizException(CodeEnum.NOT_FOUND, "商品不存在");
            }
            // 更新时先移除旧 SKU，再按新数据重建（学习项目采用简单全量替换策略）
            skuMapper.delete(Wrappers.<Sku>lambdaQuery().eq(Sku::getSpuId, spu.getId()));
        }
        spu.setTitle(dto.getTitle());
        spu.setCategoryId(dto.getCategoryId());
        spu.setBrandId(dto.getBrandId());
        spu.setDescription(dto.getDescription());
        spu.setMainImage(dto.getMainImage());
        spu.setImages(dto.getImages());
        spu.setStatus(dto.getStatus());
        if (spu.getId() == null) {
            spuMapper.insert(spu);
        } else {
            spuMapper.updateById(spu);
        }
        Long spuId = spu.getId();
        if (dto.getSkus() != null) {
            for (SkuSaveDTO s : dto.getSkus()) {
                Sku sku = new Sku();
                sku.setSpuId(spuId);
                sku.setSkuCode(s.getSkuCode());
                sku.setSpecs(s.getSpecs());
                sku.setPrice(s.getPrice());
                sku.setStock(s.getStock());
                sku.setImage(s.getImage());
                sku.setStatus(s.getStatus());
                sku.setLockedStock(0);
                sku.setSales(0);
                skuMapper.insert(sku);
            }
        }
        redisUtil.delete(String.format(RedisKeyConstants.PRODUCT_DETAIL, spuId));
        return spuId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSpu(Long spuId) {
        if (spuMapper.selectById(spuId) == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "商品不存在");
        }
        spuMapper.deleteById(spuId);
        skuMapper.delete(Wrappers.<Sku>lambdaQuery().eq(Sku::getSpuId, spuId));
        redisUtil.delete(String.format(RedisKeyConstants.PRODUCT_DETAIL, spuId));
    }

    /* ----------------------- 内部工具 ----------------------- */

    private SpuVO buildSpuVO(Spu spu, List<Sku> skus) {
        SpuVO vo = new SpuVO();
        BeanUtils.copyProperties(spu, vo);
        vo.setCategoryName(optionalName(categoryMapper, spu.getCategoryId()));
        vo.setBrandName(optionalName(brandMapper, spu.getBrandId()));
        List<SkuVO> skuVos = skus.stream().map(s -> {
            SkuVO svo = new SkuVO();
            BeanUtils.copyProperties(s, svo);
            return svo;
        }).toList();
        vo.setSkus(skuVos);
        if (!skus.isEmpty()) {
            vo.setPriceMin(skus.stream().map(Sku::getPrice).min(Comparator.naturalOrder()).orElse(null));
            vo.setPriceMax(skus.stream().map(Sku::getPrice).max(Comparator.naturalOrder()).orElse(null));
        }
        return vo;
    }

    private void fillPriceAndSales(SpuSummaryVO vo, List<Sku> skus) {
        if (skus.isEmpty()) {
            return;
        }
        vo.setPriceMin(skus.stream().map(Sku::getPrice).min(Comparator.naturalOrder()).orElse(null));
        vo.setPriceMax(skus.stream().map(Sku::getPrice).max(Comparator.naturalOrder()).orElse(null));
        vo.setSalesTotal(skus.stream().mapToInt(s -> s.getSales() == null ? 0 : s.getSales()).sum());
    }

    private String optionalName(CategoryMapper mapper, Long id) {
        if (id == null) {
            return null;
        }
        Category c = mapper.selectById(id);
        return c == null ? null : c.getName();
    }

    private String optionalName(BrandMapper mapper, Long id) {
        if (id == null) {
            return null;
        }
        Brand b = mapper.selectById(id);
        return b == null ? null : b.getName();
    }
}
