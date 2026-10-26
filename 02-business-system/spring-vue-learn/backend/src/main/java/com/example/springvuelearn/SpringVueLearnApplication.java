package com.example.springvuelearn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端启动入口。
 * 运行：mvn -f backend/pom.xml spring-boot:run
 */
@SpringBootApplication
public class SpringVueLearnApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringVueLearnApplication.class, args);
    }
}
