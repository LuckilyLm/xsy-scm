#!/usr/bin/env python3
"""W6 e2e 临时账号脚本 —— 薄封装，实现见 tools/e2e_accounts.py。

Playwright 调用约定（见 xsy-scm-web/e2e/scm-*.spec.ts）：
    env: W6_E2E_NAME / W6_E2E_PASSWORD
    args: setup | cleanup
"""
import os
import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import e2e_accounts as impl  # noqa: E402

impl.NAME = os.environ.get("W6_E2E_NAME", "")
impl.PASSWORD = os.environ.get("W6_E2E_PASSWORD", "")
impl.PREFIX = "w6_e2e_"


def _cli(flag: str) -> str:
    """允许 --name/--password 覆盖环境变量（同 w5_e2e_accounts.py）。"""
    for i, token in enumerate(sys.argv):
        if token == flag and i + 1 < len(sys.argv):
            return sys.argv[i + 1]
    return ""


def _all_cli(flag: str) -> list[str]:
    return [sys.argv[i + 1] for i, token in enumerate(sys.argv[:-1]) if token == flag]


def _schema() -> str:
    schema = impl.SCHEMA
    if not re.fullmatch(r"[a-z_][a-z0-9_]*", schema):
        raise SystemExit("[w6-e2e] 非法 PostgreSQL schema 名称")
    return schema


def _unused_report_dates() -> None:
    impl.guard()
    count = int(_cli("--count") or "2")
    if count < 1 or count > 10:
        raise SystemExit("[w6-e2e] --count 必须在 1 到 10 之间")
    schema = _schema()
    sql = f"""
        SELECT candidate.report_date::text
          FROM (
              SELECT (timezone('Asia/Shanghai', CURRENT_TIMESTAMP)::date - day_offset)::date AS report_date
                FROM generate_series(2, 90) AS day_offsets(day_offset)
          ) candidate
         WHERE NOT EXISTS (
                   SELECT 1 FROM {schema}.report_purchase_daily daily
                    WHERE daily.report_date = candidate.report_date
               )
           AND NOT EXISTS (
                   SELECT 1 FROM {schema}.purchase_order purchase_order
                    WHERE purchase_order.deleted = FALSE
                      AND purchase_order.submitted_at >= candidate.report_date::timestamp AT TIME ZONE 'Asia/Shanghai'
                      AND purchase_order.submitted_at < (candidate.report_date + 1)::timestamp AT TIME ZONE 'Asia/Shanghai'
                      AND purchase_order.status IN ('SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED')
               )
         ORDER BY candidate.report_date DESC
         LIMIT {count};
    """
    values = [line.strip() for line in impl.psql(sql, value_only=True).splitlines() if line.strip()]
    if len(values) != count:
        raise SystemExit(f"[w6-e2e] 最近 90 天内只找到 {len(values)} 个可用每日清单日期")
    print("\n".join(values))


def _backdate_purchase_order() -> None:
    impl.guard()
    try:
        report_date = date.fromisoformat(_cli("--report-date"))
    except ValueError as error:
        raise SystemExit("[w6-e2e] --report-date 必须是 yyyy-MM-dd") from error
    remark = _cli("--remark")
    ids = [int(value) for value in _all_cli("--order-id")]
    if not remark or not ids or any(order_id <= 0 for order_id in ids) or len(ids) != len(set(ids)):
        raise SystemExit("[w6-e2e] 回填提交时间需要唯一的 --order-id 列表和 --remark")
    schema = _schema()
    id_list = ",".join(str(order_id) for order_id in ids)
    sql = f"""
        UPDATE {schema}.purchase_order
           SET submitted_at = (DATE {impl.q(report_date.isoformat())} + TIME '12:00:00') AT TIME ZONE 'Asia/Shanghai'
         WHERE id IN ({id_list})
           AND remark = {impl.q(remark)}
           AND deleted = FALSE
           AND status IN ('SUBMITTED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'SHORT_CLOSED')
        RETURNING id;
    """
    updated = {
        int(line.strip())
        for line in impl.psql(sql, value_only=True).splitlines()
        if line.strip().isdigit()
    }
    if updated != set(ids):
        raise SystemExit(f"[w6-e2e] 只回填了 {len(updated)} / {len(ids)} 张指定且有效的采购单")


def _cleanup_daily_report_fixture() -> None:
    impl.guard()
    schema = _schema()
    report_dates: list[str] = []
    for value in _all_cli("--report-date"):
        try:
            report_dates.append(date.fromisoformat(value).isoformat())
        except ValueError as error:
            raise SystemExit("[w6-e2e] --report-date 必须是 yyyy-MM-dd") from error
    ids = [int(value) for value in _all_cli("--order-id")]
    remark = _cli("--remark")
    if not report_dates or len(report_dates) != len(set(report_dates)):
        raise SystemExit("[w6-e2e] 清理每日清单 fixture 需要唯一的 --report-date 列表")
    if ids and (not remark or any(order_id <= 0 for order_id in ids) or len(ids) != len(set(ids))):
        raise SystemExit("[w6-e2e] 清理采购单需要唯一的 --order-id 列表和 --remark")
    date_list = ",".join(f"DATE {impl.q(value)}" for value in report_dates)
    statements = [
        "BEGIN",
        f"DELETE FROM {schema}.report_purchase_daily_item WHERE report_date IN ({date_list})",
        f"DELETE FROM {schema}.report_purchase_daily WHERE report_date IN ({date_list})",
    ]
    if ids:
        id_list = ",".join(str(order_id) for order_id in ids)
        owner_clause = f"id IN ({id_list}) AND remark = {impl.q(remark)}"
        statements.extend([
            f"DELETE FROM {schema}.purchase_order_item WHERE purchase_order_id IN "
            f"(SELECT id FROM {schema}.purchase_order WHERE {owner_clause})",
            f"DELETE FROM {schema}.purchase_order WHERE {owner_clause}",
        ])
    statements.append("COMMIT")
    impl.psql(";\n".join(statements) + ";")


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else ""
    if _cli("--name"):
        impl.NAME = _cli("--name")
    if _cli("--password"):
        impl.PASSWORD = _cli("--password")
    # 只读伴随账号的名字在 e2e_accounts 导入期由 NAME 算出，那时 NAME 还是空串
    # （W6 的 env key 当时不在识别列表里）；这里显式重算，避免 setup 建出空 login_name。
    impl.READ_NAME = impl.NAME + "_read" if impl.NAME else ""
    if action == "setup":
        impl.setup()
    elif action == "cleanup":
        impl.cleanup()
    elif action == "unused-report-dates":
        _unused_report_dates()
    elif action == "backdate-purchase-order":
        _backdate_purchase_order()
    elif action == "cleanup-daily-report-fixture":
        _cleanup_daily_report_fixture()
    else:
        raise SystemExit(
            "用法: w6_e2e_accounts.py setup|cleanup|unused-report-dates|backdate-purchase-order|"
            "cleanup-daily-report-fixture [--name X --password Y]"
            "（或 W6_E2E_NAME/W6_E2E_PASSWORD）"
        )
