package com.example.redislab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.redislab.entity.Stock;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockMapper extends BaseMapper<Stock> {
}
