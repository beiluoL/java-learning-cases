package com.example.ai.protection.mapper;

import com.example.ai.protection.entity.CostProtectionLog;
import com.example.ai.protection.entity.CostProtectionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 成本保护规则与操作日志 Mapper。 */
@Mapper
public interface CostProtectionRuleMapper {

    List<CostProtectionRule> list();

    List<CostProtectionRule> listEnabled();

    CostProtectionRule findByCode(@Param("ruleCode") String ruleCode);

    int update(@Param("r") CostProtectionRule rule);

    int insertLog(CostProtectionLog log);

    List<CostProtectionLog> listLogs(@Param("limit") int limit);

    /** 当前仍生效的紧急开关（action=EMERGENCY_STOP 且 restored_at 为空）。 */
    CostProtectionLog findActiveEmergency();

    int restoreEmergency(@Param("id") Long id, @Param("restoredAt") LocalDateTime restoredAt);
}
