package com.finpay.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 审计切面。拦截所有带 {@link AuditLog} 的方法，记录操作前后状态。
 * 通过 {@link AuditLogRecorder} 落库；若业务未提供 recorder，则降级为仅打印日志。
 */
@Slf4j
@Aspect
@Order(1)
@Component
public class AuditLogAspect {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuditLogRecorder recorder;

    public AuditLogAspect(AuditLogRecorder recorder) {
        this.recorder = recorder;
    }

    @Around("@annotation(com.finpay.security.audit.AuditLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        AuditLog ann = method.getAnnotation(AuditLog.class);
        String operator = resolveOperator();
        String ip = resolveIp();
        String params = ann.logParams() ? safeParams(pjp.getArgs()) : "[hidden]";

        long start = System.currentTimeMillis();
        String result = "SUCCESS";
        String error = null;
        Object ret;
        try {
            ret = pjp.proceed();
        } catch (Throwable t) {
            result = "FAIL";
            error = t.getMessage();
            throw t;
        } finally {
            AuditLogRecord record = AuditLogRecord.builder()
                    .operator(operator)
                    .action(ann.action())
                    .resource(ann.resource())
                    .bizId(tryExtractBizId(pjp.getArgs()))
                    .ip(ip)
                    .params(params)
                    .result(result)
                    .error(error)
                    .occurTime(LocalDateTime.now())
                    .build();
            if (recorder != null) {
                try { recorder.record(record); } catch (Exception e) { log.warn("审计落库失败", e); }
            } else {
                log.info("[AUDIT] {} {} by {} ip={} result={}", ann.action(), ann.resource(), operator, ip, result);
            }
        }
        return ret;
    }

    private String resolveOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "internal";
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "internal";
        }
    }

    private String safeParams(Object[] args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            return "[unserializable]";
        }
    }

    private String tryExtractBizId(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object a : args) {
            if (a instanceof String s && !s.isBlank()) return s.length() > 64 ? s.substring(0, 64) : s;
        }
        return null;
    }
}
