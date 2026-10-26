#!/usr/bin/env python3
"""
从 MySQL 建表脚本自动派生 H2 兼容版本，保证两份脚本永不漂移。

用法（在项目根目录执行）：
    python3 scripts/gen_h2_schema.py

输入：src/main/resources/db/mysql/schema.sql
输出：src/main/resources/db/h2/schema-h2.sql

MySQL -> H2 的差异处理：
  1. 去掉 DROP/CREATE/USE DATABASE（H2 内存库无需建库），改为逐表 DROP TABLE IF EXISTS
  2. 去掉表选项 ENGINE=InnoDB / CHARSET / 表级 COMMENT（H2 不支持）
  3. 内联 KEY idx_xxx(...)        -> 独立 CREATE INDEX 语句（H2 不支持内联普通索引）
  4. 内联 UNIQUE KEY uk_xxx(...)  -> CONSTRAINT uk_xxx UNIQUE(...)（H2 语法）
  5. 去掉 ON UPDATE CURRENT_TIMESTAMP（更新时间由 MyBatis-Plus 审计字段自动填充）
  6. TEXT -> VARCHAR(20000)（H2 的 CLOB 映射为 String 时行为不一致，统一用大 VARCHAR）
"""
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
SRC = PROJECT_ROOT / 'src/main/resources/db/mysql/schema.sql'
DST = PROJECT_ROOT / 'src/main/resources/db/h2/schema-h2.sql'

HEADER = """-- ============================================================================
--  erp-crm-system H2 建表脚本（MySQL 兼容模式）
--  ---------------------------------------------------------------------------
--  【自动生成，请勿手工修改】
--  本文件由 scripts/gen_h2_schema.py 从 db/mysql/schema.sql 派生而来。
--  修改表结构请改 db/mysql/schema.sql，然后执行：
--      python3 scripts/gen_h2_schema.py
-- ============================================================================

"""


def parse_tables(sql: str):
    """按 CREATE TABLE 语句切分，返回 [(表名, 表体), ...]"""
    pattern = re.compile(
        r'CREATE TABLE\s+(\w+)\s*\((.*?)\)\s*ENGINE\s*=.*?;',
        re.DOTALL | re.IGNORECASE,
    )
    return [(m.group(1), m.group(2)) for m in pattern.finditer(sql)]


def split_top_level(body: str):
    """按顶层逗号切分列定义，忽略括号内的逗号（如 DECIMAL(12, 2)）"""
    parts, depth, buf = [], 0, []
    for ch in body:
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(''.join(buf))
            buf = []
        else:
            buf.append(ch)
    if ''.join(buf).strip():
        parts.append(''.join(buf))
    return parts


def convert_table(table: str, body: str):
    """返回 (建表语句, [独立索引语句...])"""
    columns, indexes = [], []
    for raw in split_top_level(body):
        line = raw.strip().rstrip(',')
        if not line:
            continue
        upper = line.upper()
        # 内联普通索引 -> 独立 CREATE INDEX
        m = re.match(r'KEY\s+(\w+)\s*\((.*?)\)\s*$', line, re.IGNORECASE | re.DOTALL)
        if m:
            indexes.append(f'CREATE INDEX {m.group(1)} ON {table} ({m.group(2).strip()});')
            continue
        # 内联唯一索引 -> UNIQUE 约束
        m = re.match(r'UNIQUE KEY\s+(\w+)\s*\((.*?)\)\s*$', line, re.IGNORECASE | re.DOTALL)
        if m:
            columns.append(f'    CONSTRAINT {m.group(1)} UNIQUE ({m.group(2).strip()})')
            continue
        if upper.startswith('PRIMARY KEY'):
            columns.append('    ' + ' '.join(line.split()))
            continue
        # 普通列：清理 MySQL 专有语法
        col = ' '.join(line.split())
        col = re.sub(r'\s*ON UPDATE CURRENT_TIMESTAMP', '', col, flags=re.IGNORECASE)
        col = re.sub(r'\bTEXT\b', 'VARCHAR(20000)', col, flags=re.IGNORECASE)
        columns.append('    ' + col)
    ddl = f'CREATE TABLE {table} (\n' + ',\n'.join(columns) + '\n);'
    return ddl, indexes


def main():
    sql = SRC.read_text(encoding='utf-8')
    tables = parse_tables(sql)
    if not tables:
        raise SystemExit('未解析到任何 CREATE TABLE，请检查源脚本格式')

    out = [HEADER]
    # 先统一 DROP，保证脚本可重复执行
    out.append('-- ---------- 清理旧表（保证脚本可重复执行） ----------')
    for name, _ in reversed(tables):
        out.append(f'DROP TABLE IF EXISTS {name};')
    out.append('')

    for name, body in tables:
        ddl, indexes = convert_table(name, body)
        out.append(f'-- ---------- {name} ----------')
        out.append(ddl)
        out.extend(indexes)
        out.append('')

    DST.parent.mkdir(parents=True, exist_ok=True)
    DST.write_text('\n'.join(out), encoding='utf-8')
    total_idx = sum(len(convert_table(n, b)[1]) for n, b in tables)
    print(f'已生成 {DST.relative_to(PROJECT_ROOT)}：{len(tables)} 张表，{total_idx} 个独立索引')


if __name__ == '__main__':
    main()
