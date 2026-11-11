package com.example.ai.common;

import java.util.List;

/** 分页统一结构，与前端 DataTable 约定一致。 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
