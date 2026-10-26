package com.example.springvuelearn.dto;

/**
 * 新增用户的入参（与实体分离，避免暴露内部字段）。
 * Java 17 record：不可变、自动生成构造器/getter/equals/hashCode/toString。
 */
public record UserCreateRequest(String username, String email) {
}
