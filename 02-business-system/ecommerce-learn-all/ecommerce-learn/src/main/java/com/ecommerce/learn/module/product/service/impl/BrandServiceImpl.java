package com.ecommerce.learn.module.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.module.product.dto.BrandSaveDTO;
import com.ecommerce.learn.module.product.dto.BrandVO;
import com.ecommerce.learn.module.product.entity.Brand;
import com.ecommerce.learn.module.product.mapper.BrandMapper;
import com.ecommerce.learn.module.product.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 品牌服务
 */
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;

    @Override
    public List<BrandVO> listAll() {
        return brandMapper.selectList(Wrappers.emptyWrapper()).stream().map(b -> {
            BrandVO vo = new BrandVO();
            BeanUtils.copyProperties(b, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void save(BrandSaveDTO dto) {
        Brand b = new Brand();
        b.setName(dto.getName());
        b.setLogo(dto.getLogo());
        b.setDescription(dto.getDescription());
        brandMapper.insert(b);
    }

    @Override
    public void remove(Long id) {
        Brand b = brandMapper.selectById(id);
        if (b == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "品牌不存在");
        }
        brandMapper.deleteById(id);
    }
}
