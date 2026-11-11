package com.example.ai.alert.mapper;

import com.example.ai.alert.entity.CostAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 成本告警 Mapper（告警中心：去重、确认、恢复、分页）。 */
@Mapper
public interface CostAlertMapper {

    int insert(CostAlert alert);

    List<CostAlert> listRecent(@Param("limit") int limit);

    /** 指纹去重：同指纹仍处于 OPEN/ACKNOWLEDGED 的告警直接累加次数，不重复落新行。 */
    CostAlert findActiveByFingerprint(@Param("fingerprint") String fingerprint);

    int bumpOccurrence(@Param("id") Long id, @Param("lastSeenAt") LocalDateTime lastSeenAt,
                       @Param("severity") String severity, @Param("message") String message);

    CostAlert findById(@Param("id") Long id);

    List<CostAlert> page(@Param("alertType") String alertType, @Param("alertLevel") String alertLevel,
                         @Param("status") String status, @Param("targetType") String targetType,
                         @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                         @Param("offset") long offset, @Param("limit") int limit);

    long count(@Param("alertType") String alertType, @Param("alertLevel") String alertLevel,
               @Param("status") String status, @Param("targetType") String targetType,
               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    int acknowledge(@Param("id") Long id, @Param("by") String by, @Param("at") LocalDateTime at);

    int resolve(@Param("id") Long id, @Param("at") LocalDateTime at);

    long countOpenByLevel(@Param("level") String level);

    /**
     * 统计未恢复的高等级告警条数，可排除指定类型。
     *
     * <p>用于保护等级判定时排除 PROTECTION_ACTION：那条告警本身就是保护引擎「已经动手」的审计记录，
     * 若再拿它当风险输入，会形成「降级→写告警→告警又要求降级」的自锁，管理员恢复后永远回不到 NORMAL。
     */
    long countOpenByLevelExcluding(@Param("level") String level, @Param("excludeType") String excludeType);

    /** 某对象下所有未恢复告警，供阈值预警判断「这一档是否已经报过 / 是否该恢复」。 */
    List<CostAlert> listOpenByTypeAndTarget(@Param("alertType") String alertType,
                                            @Param("targetType") String targetType,
                                            @Param("targetId") String targetId);
}
