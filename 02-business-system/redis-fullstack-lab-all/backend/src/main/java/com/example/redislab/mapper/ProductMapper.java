package com.example.redislab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.redislab.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
