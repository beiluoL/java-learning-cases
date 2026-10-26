#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
erp-crm-system 数据脚本生成器
------------------------------------------------------------------------
设计原则（与 gen_h2_schema.py 一致，单一数据源、避免分叉）：
  - 手工维护的可读分片位于 src/main/resources/db/mysql/_dN.sql
  - 本脚本把分片按序合并为 MySQL 权威数据脚本 db/mysql/data.sql
  - 再对 data.sql 做「MySQL -> H2(MODE=MySQL)」轻量清洗，产出
    db/h2/data-h2.sql，供 H2 profile 的 sql.init 使用。

清洗规则（H2 MySQL 兼容模式下通常无需改动，这里仅做防御性处理）：
  1. INSERT IGNORE  -> INSERT
  2. 去掉 `ON DUPLICATE KEY UPDATE ...;`
  3. 去掉标识符反引号 `
（当前分片均为标准 SQL，data-h2.sql 与 data.sql 内容等价。）
"""
import pathlib
import re

BASE = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/db"
MYSQL_DIR = BASE / "mysql"
H2_DIR = BASE / "h2"

# 分片顺序即入库顺序（无物理外键，顺序不影响约束，但保证可读性）
FRAGMENTS = ["_d1.sql", "_d2.sql", "_d3.sql"]

# 顶部说明头
HEADER = """-- ============================================================================
--  erp-crm-system 初始化数据（MySQL 权威脚本，由 scripts/gen_data.py 自动合并）
--  ---------------------------------------------------------------------------
--  由 src/main/resources/db/mysql/_d1.sql ~ _d3.sql 按顺序合并生成，
--  请勿手工直接编辑本文件；修改请改分片后执行: python3 scripts/gen_data.py
--  演示账号（密码统一 123456，BCrypt 密文）：
--    admin     超级管理员      全部数据权限
--    zhangsan  销售经理        本部门及以下
--    lisi      销售专员        仅本人
--    wangwu    采购专员        本部门
--    zhaoliu   仓库管理员      本部门
--    sunqi     人事经理        全部
--    zhouba    销售二部专员    仅本人
-- ============================================================================
"""


def build_mysql_data() -> str:
    chunks = [HEADER.strip()]
    for frag in FRAGMENTS:
        p = MYSQL_DIR / frag
        if not p.exists():
            raise FileNotFoundError(f"缺失分片: {p}")
        chunks.append(p.read_text(encoding="utf-8").strip())
    # 用两个换行分隔各分片
    return "\n\n".join(chunks) + "\n"


def sanitize_for_h2(sql: str) -> str:
    # 1. INSERT IGNORE -> INSERT
    sql = re.sub(r"(?i)\bINSERT\s+IGNORE\b", "INSERT", sql)
    # 2. 去除 ON DUPLICATE KEY UPDATE ...;（单行内）
    sql = re.sub(r"(?i)\bON\s+DUPLICATE\s+KEY\s+UPDATE\b[^\n;]*;", ";", sql)
    # 3. 去除反引号
    sql = sql.replace("`", "")
    return sql


def main() -> None:
    data_mysql = build_mysql_data()
    (MYSQL_DIR / "data.sql").write_text(data_mysql, encoding="utf-8")

    data_h2 = sanitize_for_h2(data_mysql)
    H2_DIR.mkdir(parents=True, exist_ok=True)
    (H2_DIR / "data-h2.sql").write_text(data_h2, encoding="utf-8")

    print(f"[OK] 合并生成: {MYSQL_DIR / 'data.sql'}")
    print(f"[OK] 派生生成: {H2_DIR / 'data-h2.sql'}")
    print(f"[OK] MySQL 行数: {data_mysql.count(chr(10)) + 1}  H2 行数: {data_h2.count(chr(10)) + 1}")


if __name__ == "__main__":
    main()
