package com.enterprise.erpcrm.framework.security.aspect;

import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.OperLogRecord;
import com.enterprise.erpcrm.framework.security.annotation.OperLogSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作日志切面：环绕标注 {@link OperLog} 的方法，无论成功失败均异步落库一条操作记录。
 * <p>通过 {@link OperLogSink}（system 模块实现）持久化，记录操作者、IP、URL、参数、耗时、成败。</p>
 *
 * @author erp-crm-system
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final OperLogSink operLogSink;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        String errorMsg = null;
        boolean success = true;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            errorMsg = t.getMessage();
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - start;
            try {
                buildAndSave(joinPoint, operLog, success, errorMsg, cost);
            } catch (Exception e) {
                log.warn("操作日志写入失败：{}", e.getMessage());
            }
        }
    }

    private void buildAndSave(ProceedingJoinPoint joinPoint, OperLog operLog, boolean success,
                              String errorMsg, long cost) {
        MethodSignature ms = (MethodSignature) joinPoint.getSignature();
        Method method = ms.getMethod();
        HttpServletRequest request = currentRequest();

        OperLogRecord.OperLogRecordBuilder builder = OperLogRecord.builder()
                .title(operLog.title())
                .businessType(operLog.businessType().getValue())
                .method(method.getDeclaringClass().getName() + "." + method.getName())
                .requestMethod(request != null ? request.getMethod() : "")
                .operUrl(request != null ? request.getRequestURI() : "")
                .operParam(buildParam(joinPoint))
                .status(success ? 0 : 1)
                .errorMsg(errorMsg)
                .operTime(LocalDateTime.now())
                .costTime(cost);

        var user = SecurityHolder.get();
        if (user != null) {
            builder.operName(user.getUsername()).operUserId(user.getUserId());
        }
        if (request != null) {
            builder.operIp(resolveIp(request));
        }
        operLogSink.save(builder.build());
    }

    private String buildParam(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg instanceof jakarta.servlet.ServletRequest
                        || arg instanceof jakarta.servlet.ServletResponse
                        || arg instanceof jakarta.servlet.http.HttpSession) {
                    continue;
                }
                sb.append(objectMapper.writeValueAsString(arg)).append(" ");
            }
            String s = sb.toString().trim();
            return s.length() > 2000 ? s.substring(0, 2000) : s;
        } catch (Exception e) {
            return "";
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return Objects.requireNonNullElse(ip, "0:0:0:0");
    }
}
