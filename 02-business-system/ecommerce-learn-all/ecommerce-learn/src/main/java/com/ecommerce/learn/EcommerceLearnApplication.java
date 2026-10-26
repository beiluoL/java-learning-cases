package com.ecommerce.learn;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 电商与零售学习项目 - 启动类
 * <p>
 * 模块扫描说明：
 * - @MapperScan 扫描所有 Mapper 接口（MyBatis-Plus）
 * - 业务包位于 com.ecommerce.learn.module.* 下，按 用户/商品/购物车/订单/支付/秒杀/后台 垂直拆分
 * </p>
 *
 * @author ecommerce-learn
 */
@SpringBootApplication
@MapperScan("com.ecommerce.learn.module.**.mapper")
public class EcommerceLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcommerceLearnApplication.class, args);
    }
}
