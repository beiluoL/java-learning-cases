package com.enterprise.erpcrm.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.enterprise.erpcrm.framework.handler.MyMetaObjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 核心配置。
 * <ul>
 *   <li>分页插件 {@link PaginationInnerInterceptor}：统一物理分页，避免内存分页；</li>
 *   <li>乐观锁插件 {@link OptimisticLockerInnerInterceptor}：库存扣减等并发场景防超卖；</li>
 *   <li>审计字段自动填充 {@link MetaObjectHandler}：create_time / update_time 由框架填充。</li>
 * </ul>
 * 逻辑删除由 application.yml 的 {@code logic-delete-field=deleted} 全局生效，无需逐个实体注解。
 *
 * @author erp-crm-system
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页（MySQL 方言；H2 MySQL 兼容模式同样适用）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁：实体带 @Version 字段即可生效
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MyMetaObjectHandler();
    }
}
