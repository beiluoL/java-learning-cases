package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mq.entity.MqStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存 Mapper。
 * <p>
 * <b>防超卖的核心就在 {@link #deduct} 这一条 SQL：</b>
 * <pre>
 * UPDATE mq_stock SET available = available - #{quantity}
 *  WHERE product_id = #{productId} AND available >= #{quantity}
 * </pre>
 * ① 单条 UPDATE 在 InnoDB 内是原子的，行锁保证并发下不会两个事务同时读到 20 再各扣 1；
 * ② {@code available >= quantity} 把"判断"塞进 SQL，杜绝了「先查后扣」的经典竞态；
 * ③ 返回值 = 影响行数，0 就代表库存不足 —— 用返回值做分支，而不是再查一次库。
 */
@Mapper
public interface MqStockMapper extends BaseMapper<MqStock> {

    @Update("""
            UPDATE mq_stock
               SET available = available - #{quantity},
                   version = version + 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE product_id = #{productId}
               AND available >= #{quantity}
            """)
    int deduct(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /** 回补库存（订单取消时调用），无下限校验，允许超过 total 由业务层兜底 */
    @Update("""
            UPDATE mq_stock
               SET available = available + #{quantity},
                   version = version + 1,
                   update_time = CURRENT_TIMESTAMP
             WHERE product_id = #{productId}
            """)
    int restore(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Select("SELECT * FROM mq_stock WHERE product_id = #{productId}")
    MqStock selectByProductId(@Param("productId") Long productId);
}
