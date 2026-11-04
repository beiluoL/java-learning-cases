package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.mq.entity.MqOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 Mapper。
 * <p>
 * 所有状态流转都用 <b>CAS（compare-and-set）</b>：{@code WHERE order_no = ? AND status = ?}。
 * 这样即使「用户点支付」和「超时取消消费」同时到达，也只有一方能成功，
 * 不会出现"已支付订单被取消"这种资金级事故。
 */
@Mapper
public interface MqOrderMapper extends BaseMapper<MqOrder> {

    /** 排队中 → 待支付：异步落单成功时调用，同时回填金额/商品名/支付截止时间 */
    @Update("""
            UPDATE mq_order
               SET status = 'UNPAID',
                   amount = #{amount},
                   product_name = #{productName},
                   expire_time = #{expireTime},
                   delay_seconds = #{delaySeconds},
                   update_time = CURRENT_TIMESTAMP
             WHERE order_no = #{orderNo}
               AND status = 'QUEUING'
            """)
    int confirmCreated(@Param("orderNo") String orderNo,
                       @Param("amount") BigDecimal amount,
                       @Param("productName") String productName,
                       @Param("expireTime") LocalDateTime expireTime,
                       @Param("delaySeconds") Integer delaySeconds);

    /** 排队中 → 失败（库存不足 / 落单异常） */
    @Update("""
            UPDATE mq_order
               SET status = 'FAILED',
                   cancel_reason = #{reason},
                   cancel_time = CURRENT_TIMESTAMP,
                   update_time = CURRENT_TIMESTAMP
             WHERE order_no = #{orderNo}
               AND status = 'QUEUING'
            """)
    int markFailed(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /** 待支付 → 已支付（支付回调 / 用户主动支付） */
    @Update("""
            UPDATE mq_order
               SET status = 'PAID',
                   pay_time = CURRENT_TIMESTAMP,
                   update_time = CURRENT_TIMESTAMP
             WHERE order_no = #{orderNo}
               AND status = 'UNPAID'
            """)
    int markPaid(@Param("orderNo") String orderNo);

    /** 待支付 → 已取消（超时取消 / 用户主动取消），返回 0 说明状态已变（如已支付） */
    @Update("""
            UPDATE mq_order
               SET status = 'CANCELLED',
                   cancel_time = CURRENT_TIMESTAMP,
                   cancel_reason = #{reason},
                   update_time = CURRENT_TIMESTAMP
             WHERE order_no = #{orderNo}
               AND status = 'UNPAID'
            """)
    int markCancelled(@Param("orderNo") String orderNo, @Param("reason") String reason);

    /**
     * 通用状态 CAS，供补偿/管理端使用。
     */
    @Update("""
            UPDATE mq_order
               SET status = #{toStatus},
                   update_time = CURRENT_TIMESTAMP
             WHERE order_no = #{orderNo}
               AND status = #{fromStatus}
            """)
    int casStatus(@Param("orderNo") String orderNo,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus);

    /**
     * 分页查询（写在 MqOrderMapper.xml 里，演示注解与 XML 混用的标准写法）。
     * MyBatis-Plus 只要第一个参数是 IPage，就会自动做 count + limit 改写。
     */
    IPage<MqOrder> selectOrderPage(IPage<MqOrder> page,
                                   @Param("status") String status,
                                   @Param("keyword") String keyword,
                                   @Param("userId") Long userId);
}
