package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类。
 *
 * <p>@SpringBootApplication 开启自动配置、组件扫描（默认扫描本类所在包及其子包）。
 * @MapperScan 指定 MyBatis Mapper 接口的扫描路径，使接口无需逐个标注 @Mapper。
 */
@SpringBootApplication
@MapperScan("com.example.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
