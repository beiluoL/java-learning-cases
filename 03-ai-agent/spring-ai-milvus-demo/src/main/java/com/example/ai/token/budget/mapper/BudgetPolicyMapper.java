package com.example.ai.token.budget.mapper;

import com.example.ai.token.budget.entity.BudgetPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 预算策略 Mapper。 */
@Mapper
public interface BudgetPolicyMapper {

    List<BudgetPolicy> list();

    BudgetPolicy findById(@Param("id") Long id);

    BudgetPolicy findScope(@Param("budgetType") String budgetType, @Param("targetId") String targetId);

    int insert(BudgetPolicy policy);

    int update(BudgetPolicy policy);
}
