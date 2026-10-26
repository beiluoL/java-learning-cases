package com.learn.mybatismini.demo.mapper;

import com.learn.mybatismini.annotations.Param;
import com.learn.mybatismini.demo.entity.Order;

import java.util.List;

/**
 * 订单 Mapper 接口（XML 驱动）。
 *
 * <p>与 UserMapper 不同，本接口对应的 {@code OrderMapper.xml} 顶部加了 {@code <cache/>}，
 * 因此这个 namespace 下的查询都会走<b>二级缓存</b>。Demo 里用它演示"跨 SqlSession 命中二级缓存"。
 */
public interface OrderMapper {

    List<Order> selectAll();

    List<Order> selectByUserId(@Param("userId") Long userId);

    List<Order> selectByStatus(@Param("status") String status);
}
