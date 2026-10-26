package com.example.ssm.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日志切面（AOP 模块）：对 Service 层方法进行环绕增强，
 * 统一打印「方法名 + 入参 + 耗时 + 异常」，便于定位问题与性能观测。
 *
 * 切面 bean 由 Spring 根容器扫描创建（applicationContext.xml 已开启 aop:aspectj-autoproxy），
 * 因此只对根容器中的 Service Bean 生效，与 Web 子容器互不干扰。
 */
@Aspect
@Component
public class WebLogAspect {

    private static final Logger log = LoggerFactory.getLogger(WebLogAspect.class);

    /** 切入点：com.example.ssm.service 包下所有类的所有方法 */
    @Pointcut("execution(* com.example.ssm.service..*(..))")
    public void serviceLog() {
    }

    @Around("serviceLog()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String signature = joinPoint.getSignature().toShortString();
        log.info("[SERVICE] {} 开始, 参数={}", signature, joinPoint.getArgs());
        try {
            Object result = joinPoint.proceed();
            log.info("[SERVICE] {} 完成, 耗时={}ms", signature, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            log.error("[SERVICE] {} 异常, 耗时={}ms", signature, System.currentTimeMillis() - start, t);
            throw t;
        }
    }
}
