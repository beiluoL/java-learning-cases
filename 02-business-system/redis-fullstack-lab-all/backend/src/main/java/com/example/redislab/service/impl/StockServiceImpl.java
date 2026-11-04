package com.example.redislab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.redislab.entity.Stock;
import com.example.redislab.mapper.StockMapper;
import com.example.redislab.service.StockService;
import org.springframework.stereotype.Service;

/**
 * 库存服务。decrement 用「UPDATE ... SET count=count-1 WHERE product_id=? AND count>0」，
 * 在数据库层用 WHERE count>0 兜底，即使分布式锁有缝隙也不会出现负库存（双重保险）。
 */
@Service
public class StockServiceImpl implements StockService {

    private final StockMapper stockMapper;

    public StockServiceImpl(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Override
    public int getCurrentCount(Long productId) {
        Stock s = stockMapper.selectOne(new QueryWrapper<Stock>().eq("product_id", productId));
        return s == null ? 0 : s.getCount();
    }

    @Override
    public boolean decrement(Long productId) {
        int rows = stockMapper.update(null,
                new UpdateWrapper<Stock>()
                        .eq("product_id", productId)
                        .gt("count", 0)
                        .setSql("count = count - 1"));
        return rows > 0;
    }

    @Override
    public void reset(Long productId, int count) {
        Stock s = stockMapper.selectOne(new QueryWrapper<Stock>().eq("product_id", productId));
        if (s != null) {
            s.setCount(count);
            stockMapper.updateById(s);
        }
    }
}
