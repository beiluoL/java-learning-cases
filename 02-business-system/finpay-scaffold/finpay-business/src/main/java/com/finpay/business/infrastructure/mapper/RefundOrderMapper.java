package com.finpay.business.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finpay.business.infrastructure.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {
}
