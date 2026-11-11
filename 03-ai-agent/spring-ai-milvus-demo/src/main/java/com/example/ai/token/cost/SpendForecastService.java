package com.example.ai.token.cost;

import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.analytics.mapper.CostAnalyticsMapper;
import com.example.ai.token.budget.entity.BudgetPolicy;
import com.example.ai.token.budget.mapper.BudgetPolicyMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消费速度与预算耗尽预测。
 *
 * <p>核心公式：预计周期成本 = 当前累计成本 / 已过去时间 × 周期总时间。
 * 同时给出「按最近 24 小时速度」的滚动预测作为对照，
 * 因为月初用「已过去时间」外推会严重高估（样本太少）。
 *
 * <p>预测是决策辅助，不是账单；触发告警时会把口径写进 message。
 */
@Service
public class SpendForecastService {

    private final CostAnalyticsMapper analyticsMapper;
    private final BudgetPolicyMapper policyMapper;
    private final CostAlertService alertService;
    private final UserIdentityProvider identityProvider;
    private final Clock clock;

    public SpendForecastService(CostAnalyticsMapper analyticsMapper, BudgetPolicyMapper policyMapper,
                                CostAlertService alertService, UserIdentityProvider identityProvider, Clock clock) {
        this.analyticsMapper = analyticsMapper;
        this.policyMapper = policyMapper;
        this.alertService = alertService;
        this.identityProvider = identityProvider;
        this.clock = clock;
    }

    public record Forecast(String period, String currentCost, String projectedCost, String budgetLimit,
                           double usageRate, boolean willExceed, String exhaustionEtaHours,
                           String rolling24hCost, String rollingProjectedCost, String basis) {
    }

    public Forecast monthly() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate firstDay = now.toLocalDate().withDayOfMonth(1);
        LocalDateTime monthStart = firstDay.atStartOfDay();
        int daysInMonth = firstDay.lengthOfMonth();
        double elapsedDays = Math.max(Duration.between(monthStart, now).toMinutes() / 1440d, 1d / 24d);

        BigDecimal current = Views.decimal(analyticsMapper.overview(monthStart, now).get("totalCost"));
        BigDecimal projected = current.divide(BigDecimal.valueOf(elapsedDays), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(daysInMonth));

        BigDecimal rolling = Views.decimal(analyticsMapper.overview(now.minusHours(24), now).get("totalCost"));
        BigDecimal rollingProjected = rolling.multiply(BigDecimal.valueOf(daysInMonth));

        String userId = identityProvider.currentUserId();
        BudgetPolicy monthly = policyMapper.findScope("MONTHLY", userId);
        BigDecimal limit = monthly == null ? null : monthly.getMonthlyLimit();
        if (limit == null) {
            BudgetPolicy global = policyMapper.findScope("GLOBAL", "__ALL__");
            limit = global == null ? null : global.getMonthlyLimit();
        }
        boolean willExceed = limit != null && limit.signum() > 0 && projected.compareTo(limit) > 0;
        double usageRate = limit == null || limit.signum() == 0 ? 0d
                : current.divide(limit, 6, RoundingMode.HALF_UP).doubleValue();

        if (willExceed) {
            alertService.alert(AiEnums.AlertType.FORECAST_EXHAUST, AiEnums.AlertLevel.HIGH, "USER", userId,
                    "按当前速度预测本月成本 " + projected.setScale(6, RoundingMode.HALF_UP)
                            + " USD 将超出月度预算 " + limit + " USD（已用 " + current + " USD）",
                    limit, projected, null);
        }
        checkVelocity(userId, rolling);
        return new Forecast("MONTHLY", current.toPlainString(), projected.toPlainString(),
                limit == null ? "" : limit.toPlainString(), usageRate, willExceed,
                "", rolling.toPlainString(), rollingProjected.toPlainString(),
                "projected=当前成本/已过去天数×当月天数；rolling 为最近24小时速度外推，月初样本少时以 rolling 为准");
    }

    /** 日预算耗尽预计时间：剩余额度 / 今日每小时消耗速度。 */
    public String dailyExhaustionEtaHours() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        double elapsedHours = Math.max(Duration.between(dayStart, now).toMinutes() / 60d, 1d / 60d);
        BigDecimal todayCost = Views.decimal(analyticsMapper.overview(dayStart, now).get("totalCost"));
        BudgetPolicy daily = policyMapper.findScope("DAILY", identityProvider.currentUserId());
        BigDecimal limit = daily == null ? null : daily.getDailyLimit();
        if (limit == null || limit.signum() <= 0 || todayCost.signum() <= 0) {
            return "";
        }
        BigDecimal perHour = todayCost.divide(BigDecimal.valueOf(elapsedHours), 8, RoundingMode.HALF_UP);
        BigDecimal remaining = limit.subtract(todayCost);
        if (remaining.signum() <= 0) {
            return "0";
        }
        return remaining.divide(perHour, 2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 消费速度突增检测：最近 1 小时成本显著高于最近 24 小时的平均每小时成本。
     *
     * <p>用「相对自己近 24 小时的基线」而不是固定金额阈值，是为了适配不同量级的业务；
     * 同时要求绝对金额 &gt; 0.01 USD，避免小额噪声（一两次请求）被判成突增。
     */
    private void checkVelocity(String userId, BigDecimal rolling24h) {
        BigDecimal lastHour = Views.decimal(analyticsMapper.overview(
                LocalDateTime.now(clock).minusHours(1), LocalDateTime.now(clock)).get("totalCost"));
        BigDecimal floor = new BigDecimal("0.01");
        if (lastHour.compareTo(floor) <= 0) {
            return;
        }
        BigDecimal avgPerHour = rolling24h.divide(BigDecimal.valueOf(24), 8, RoundingMode.HALF_UP);
        if (avgPerHour.signum() <= 0 || lastHour.compareTo(avgPerHour.multiply(BigDecimal.valueOf(3))) <= 0) {
            return;
        }
        alertService.alert(AiEnums.AlertType.SPEND_VELOCITY, AiEnums.AlertLevel.HIGH, "USER", userId,
                "最近 1 小时成本 " + lastHour.setScale(6, RoundingMode.HALF_UP) + " USD，超过近 24 小时均值 "
                        + avgPerHour.setScale(6, RoundingMode.HALF_UP) + " USD/小时 的 3 倍，疑似消费速度突增",
                avgPerHour.multiply(BigDecimal.valueOf(3)), lastHour, null);
    }

    /** 每小时自检一次，预测超支提前告警（告警中心自动去重，不会刷屏）。 */
    @Scheduled(fixedDelay = 1_800_000L, initialDelay = 120_000L)
    public void scheduledCheck() {
        try {
            monthly();
        } catch (RuntimeException e) {
            // 定时任务失败不影响主业务
        }
    }

    public Map<String, Object> detail() {
        Forecast f = monthly();
        return Map.of("monthly", f, "dailyExhaustionEtaHours", dailyExhaustionEtaHours());
    }
}
