package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mq.entity.MqProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 商品 Mapper：变更时 version 自增，作为事件版本号 */
@Mapper
public interface MqProductMapper extends BaseMapper<MqProduct> {

    /**
     * 带版本号的条件更新（乐观锁）：
     * 并发修改时只有一个请求能命中 {@code version = 期望值}，其余返回 0，
     * 由业务层决定重试或直接报「请重试」，避免两次变更的事件版本号相同导致下游无法判序。
     */
    @Update("""
            UPDATE mq_product
               SET name = #{name},
                   price = #{price},
                   status = #{status},
                   version = version + 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
               AND version = #{expectedVersion}
            """)
    int updateWithVersion(@Param("id") Long id,
                          @Param("name") String name,
                          @Param("price") BigDecimal price,
                          @Param("status") Integer status,
                          @Param("expectedVersion") Integer expectedVersion);
}
