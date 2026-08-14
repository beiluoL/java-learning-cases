package com.example.csagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能客服 Agent 后端启动类（Spring Boot 3 + Java 17）。
 * 业务端口见 application.yml（默认 8080，context-path=/api）。
 */
@SpringBootApplication
public class CsAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CsAgentApplication.class, args);
    }
}
