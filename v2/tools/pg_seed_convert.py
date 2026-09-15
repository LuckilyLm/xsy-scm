#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Parse MySQL INSERT statements from the SmartAdmin reference dump and re-emit them
as PostgreSQL INSERTs.

Used by the V2 migration to convert the SmartAdmin baseline seed data
(t_menu / t_config / t_dict / t_dict_data / ...) from MySQL to PostgreSQL:

  * backtick identifiers        -> plain identifiers
  * tinyint(1) 0/1 boolean cols -> TRUE / FALSE
  * explicit column list        -> preserved, or synthesised from the DDL

Usage:
  python pg_seed_convert.py dump.sql t_menu [t_config ...]        # emit SQL
  python pg_seed_convert.py dump.sql t_menu --inspect             # emit TSV for review
"""
import re
import sys
from pathlib import Path

# Boolean (tinyint(1) 0/1) column positions, 1-indexed, per table.
# Only needed when the INSERT has no explicit column list.
BOOL_POSITIONS = {
    "t_menu": [13, 15, 16, 17, 18],
    "t_position": [5],
    "t_employee": [12, 13, 14],
    "t_dict": [5],
    "t_dict_data": [8],
    "t_config": [],
    "t_department": [],
    "t_role": [],
    "t_role_employee": [],
    "t_role_menu": [],
    "t_role_data_scope": [],
}

# Column lists (DDL order) for tables whose INSERT omits them.
COLUMNS = {
    "t_menu": [
        "menu_id", "menu_name", "menu_type", "parent_id", "sort", "path",
        "component", "perms_type", "api_perms", "web_perms", "icon",
        "context_menu_id", "frame_flag", "frame_url", "cache_flag",
        "visible_flag", "disabled_flag", "deleted_flag", "create_user_id",
        "create_time", "update_user_id", "update_time",
    ],
    "t_department": [
        "department_id", "department_name", "manager_id", "parent_id", "sort",
        "update_time", "create_time",
    ],
    "t_dict": [
        "dict_id", "dict_name", "dict_code", "remark", "disabled_flag",
        "create_time", "update_time",
    ],
    "t_dict_data": [
        "dict_data_id", "dict_id", "data_value", "data_label", "data_style",
        "remark", "sort_order", "disabled_flag", "create_time", "update_time",
    ],
    "t_config": [
        "config_id", "config_name", "config_key", "config_value", "remark",
        "update_time", "create_time",
    ],
    "t_role": [
        "role_id", "role_name", "role_code", "remark", "update_time", "create_time",
    ],
    "t_role_employee": [
        "id", "role_id", "employee_id", "update_time", "create_time",
    ],
    "t_role_menu": [
        "role_menu_id", "role_id", "menu_id", "update_time", "create_time",
    ],
    "t_position": [
        "position_id", "position_name", "position_level", "sort", "remark",
        "deleted_flag", "create_time", "update_time",
    ],
}


def split_tuple(s: str):
    """Split a SQL VALUES tuple body into raw tokens, respecting quotes."""
    out, buf, i, n = [], [], 0, len(s)
    in_str = False
    while i < n:
        ch = s[i]
        if in_str:
            if ch == "\\" and i + 1 < n:
                buf.append(s[i:i + 2]); i += 2; continue
            if ch == "'":
                if i + 1 < n and s[i + 1] == "'":
                    buf.append("''"); i += 2; continue
                in_str = False
                buf.append(ch); i += 1; continue
            buf.append(ch); i += 1; continue
        if ch == "'":
            in_str = True; buf.append(ch); i += 1; continue
        if ch == ",":
            out.append("".join(buf).strip()); buf = []; i += 1; continue
        buf.append(ch); i += 1
    out.append("".join(buf).strip())
    return out


def find_inserts(text: str, table: str):
    """Yield (columns_or_None, [value-tuples]) for a table."""
    pattern = re.compile(
        r"INSERT\s+INTO\s+`?" + re.escape(table) + r"`?\s*"
        r"(\(([^)]*)\))?\s*VALUES\s*(.*?);",
        re.IGNORECASE | re.DOTALL,
    )
    for m in pattern.finditer(text):
        colblob = m.group(2)
        cols = None
        if colblob:
            cols = [c.strip().strip("`") for c in colblob.split(",")]
        body = m.group(3)
        for tm in re.finditer(r"\(((?:[^'()]|'(?:\\.|''|[^'])*')*)\)", body, re.DOTALL):
            yield cols, split_tuple(tm.group(1))


def to_pg_value(raw: str, is_bool: bool) -> str:
    if raw.upper() == "NULL":
        return "NULL"
    if is_bool:
        v = raw.strip()
        if v in ("1", "'1'"):
            return "TRUE"
        if v in ("0", "'0'"):
            return "FALSE"
        return v
    if raw.startswith("'"):
        return raw
    return raw


def main():
    dump = Path(sys.argv[1])
    tables = [a for a in sys.argv[2:] if not a.startswith("--")]
    inspect = "--inspect" in sys.argv
    text = dump.read_text(encoding="utf-8", errors="replace")

    for table in tables:
        cols = COLUMNS.get(table)
        bools = set(BOOL_POSITIONS.get(table, []))
        rows = []
        for icols, vals in find_inserts(text, table):
            rows.append((icols or cols, vals))

        if inspect:
            print(f"### {table}: {len(rows)} rows")
            for c, v in rows:
                print(" | ".join(v))
            print()
            continue

        print(f"-- ===== {table} ({len(rows)} rows) =====")
        for c, v in rows:
            if c is None:
                raise SystemExit(f"no column list for {table} and no spec in COLUMNS")
            rendered = [to_pg_value(x, (i + 1) in bools) for i, x in enumerate(v)]
            print("INSERT INTO %s (%s) VALUES (%s);" % (table, ", ".join(c), ", ".join(rendered)))
        print()


if __name__ == "__main__":
    main()
