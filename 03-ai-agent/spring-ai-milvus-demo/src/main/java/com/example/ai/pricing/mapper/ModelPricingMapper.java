package com.example.ai.pricing.mapper;

import com.example.ai.pricing.entity.ModelPricing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 模型价格 Mapper。价格匹配永远是「按生效时间取当前有效版本」，绝不读 Java 常量。 */
@Mapper
public interface ModelPricingMapper {

    ModelPricing findEffective(@Param("provider") String provider,
                               @Param("modelName") String modelName,
                               @Param("modelType") String modelType,
                               @Param("at") LocalDateTime at);

    List<ModelPricing> listCurrent();

    List<ModelPricing> history(@Param("modelName") String modelName);

    ModelPricing findById(@Param("id") Long id);

    int insert(ModelPricing pricing);

    int update(ModelPricing pricing);
}
