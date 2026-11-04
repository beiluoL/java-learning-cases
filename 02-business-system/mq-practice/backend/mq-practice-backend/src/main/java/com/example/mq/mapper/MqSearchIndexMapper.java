package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mq.entity.MqSearchIndex;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 检索索引（模拟 ES）Mapper */
@Mapper
public interface MqSearchIndexMapper extends BaseMapper<MqSearchIndex> {

    @Select("SELECT * FROM mq_search_index WHERE product_id = #{productId}")
    MqSearchIndex selectByProductId(@Param("productId") Long productId);
}
