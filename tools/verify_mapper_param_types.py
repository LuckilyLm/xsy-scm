#!/usr/bin/env python3
"""检测 mapper 中「日期列 CAST 后与未 CAST 参数比较」这一类 PostgreSQL 缺陷。

背景
----
MySQL 会把字符串隐式转成日期，PostgreSQL 不会：

    AND CAST(create_time AS DATE) >= #{query.startDate}

当 `startDate` 的 Java 类型是 `String` 时，JDBC 以 varchar 绑定，PostgreSQL 直接报：

    ERROR: operator does not exist: date >= character varying

`OperateLogQueryForm.startDate` / `LoginLogQueryForm.startDate` 就是这种情况。
静态 `PREPARE` 门禁抓不到它——未定型参数会被推断成 `date` 从而通过；
只有真实运行、参数带真实 JDBC 类型时才会暴露。

规则
----
1. 在每个 mapper 语句体内匹配 `CAST(<列> AS DATE) <比较符> #{a.b}`，且右侧未被 CAST 包裹；
2. 由 XML 的 `namespace` 定位 Java Mapper 接口，按语句 `id` 定位方法，取第一个参数类型；
3. 在该参数类（含父类链）里解析字段 `b` 的声明类型；
4. 日期/时间类型安全；其余（尤其 String）判为缺陷。

按方法签名解析，而不是按字段名全局查表——后者会被同名不同类（如
OperateLogQueryForm.startDate=String 与 FeedbackQueryForm.startDate=LocalDate）误伤。

用法
----
    python tools/verify_mapper_param_types.py            # 检查
    python tools/verify_mapper_param_types.py --verbose  # 打印全部命中
退出码 0 = 无缺陷，1 = 有缺陷。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVER = ROOT / "xsy-scm-server"

DATE_SAFE = {
    "LocalDate", "LocalDateTime", "Date", "Instant", "OffsetDateTime",
    "ZonedDateTime", "java.time.LocalDate", "java.time.LocalDateTime",
    "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
}

RE_NS = re.compile(r'namespace\s*=\s*"([^"]+)"')
RE_STMT = re.compile(
    r"<(select|insert|update|delete)\b[^>]*?\bid\s*=\s*\"([^\"]+)\"[^>]*?>(.*?)</\1>", re.S)
# CAST(<col> AS DATE) >= #{...}   —— 比较符可能被 XML 转义
RE_DATE_CMP = re.compile(
    r"CAST\([^()]*?\s+AS\s+DATE\)\s*(?:&gt;=|&lt;=|&gt;|&lt;|<>|!=|=)\s*#\{([^}]+)\}", re.I)
RE_FIELD = re.compile(r"^\s*private\s+([A-Za-z_][\w.<>\[\]]*)\s+([A-Za-z_]\w*)\s*;", re.M)
# 接口方法通常不写 public（隐式 public），因此 public/abstract 可选；
# 参数列表允许一层嵌套括号——@Param("query") 自带括号，否则会提前截断；
# 结尾必须是 ; 或 {，否则会把普通表达式误当方法签名。
RE_METHOD = re.compile(
    r"^\s*(?:public\s+|abstract\s+)?[\w<>\[\],.\s]+?\s+(\w+)\s*"
    r"\(((?:[^()]|\([^()]*\))*)\)\s*(?:;|\{)", re.M)
RE_EXTENDS = re.compile(r"class\s+\w+\s+extends\s+([\w.]+)")


def java_sources() -> dict[str, Path]:
    """simple class name -> 源文件（只取第一个，同名类极罕见）。"""
    out: dict[str, Path] = {}
    for java in SERVER.rglob("*.java"):
        if "target" in java.parts:
            continue
        out.setdefault(java.stem, java)
    return out


def resolve_type(srcs: dict[str, Path], simple: str, field: str) -> str | None:
    """在 simple 类及其父类链里找字段声明类型。"""
    seen: set[str] = set()
    cur = simple
    while cur and cur not in seen:
        seen.add(cur)
        path = srcs.get(cur)
        if path is None:
            return None
        text = path.read_text(encoding="utf-8", errors="replace")
        for typ, name in RE_FIELD.findall(text):
            if name == field:
                return typ
        m = RE_EXTENDS.search(text)
        if not m:
            return None
        cur = m.group(1).split(".")[-1]
    return None


def split_params(params: str) -> list[str]:
    """按逗号切参数，但忽略泛型 <> 里的逗号。"""
    out, depth, cur = [], 0, ""
    for ch in params:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur)
    return out


RE_PARAM_ANN = re.compile(r'@Param\(\s*"([^"]+)"\s*\)')


def method_param_type(srcs: dict[str, Path], namespace: str, stmt_id: str, prefix: str) -> str | None:
    """namespace 接口里 stmt_id 方法中，占位符前缀 prefix（如 query）对应的参数类型。

    形如 `List<FeedbackVO> queryPage(Page page, @Param("query") FeedbackQueryForm query)`
    ——表单往往是第二个参数，靠 @Param 名匹配，不能盲取第一个。
    """
    simple = namespace.split(".")[-1]
    path = srcs.get(simple)
    if path is None:
        return None
    text = path.read_text(encoding="utf-8", errors="replace")
    for name, params in RE_METHOD.findall(text):
        if name != stmt_id:
            continue
        parts = [p for p in split_params(params) if p.strip()]
        plain: list[str] = []
        for part in parts:
            ann = RE_PARAM_ANN.search(part)
            cleaned = re.sub(r"@\w+(\([^)]*\))?\s*", "", part).strip()
            toks = cleaned.split()
            if not toks:
                continue
            typ = toks[0].split("<")[0].split(".")[-1]
            if ann and ann.group(1) == prefix:
                return typ
            if not ann:
                plain.append(typ)
        # 未标注 @Param 且只有一个普通参数时，它就是表单
        if len(plain) == 1 and len(parts) == 1:
            return plain[0]
        return None
    return None


def main() -> int:
    verbose = "--verbose" in sys.argv
    srcs = java_sources()
    hits = 0
    findings: list[tuple[str, int, str, str, str | None]] = []

    for xml in sorted(SERVER.rglob("*.xml")):
        if "target" in xml.parts or "migration" in xml.parts:
            continue
        text = xml.read_text(encoding="utf-8", errors="replace")
        ns = RE_NS.search(text)
        if not ns:
            continue
        namespace = ns.group(1)
        rel = xml.relative_to(SERVER).as_posix()
        for m in RE_STMT.finditer(text):
            stmt_id, body = m.group(2), m.group(3)
            base = text.count("\n", 0, m.start(3)) + 1
            for cm in RE_DATE_CMP.finditer(body):
                expr = cm.group(1)
                if "." not in expr:
                    continue          # 直接方法参数，非表单字段，跳过
                hits += 1
                prefix, form_field = expr.split(".", 1)
                prefix = prefix.strip()
                form_field = form_field.split(".")[-1].strip()
                ptype = method_param_type(srcs, namespace, stmt_id, prefix)
                ftype = resolve_type(srcs, ptype, form_field) if ptype else None
                lineno = base + body.count("\n", 0, cm.start())
                findings.append((rel, lineno, expr, f"{ptype}.{form_field}", ftype))

    print(f"[mapper-date-param] 扫描命中 CAST(... AS DATE) 比较 = {hits}")
    defects = [f for f in findings if f[4] and f[4] not in DATE_SAFE]
    unresolved = [f for f in findings if f[4] is None]
    for rel, lineno, expr, path, ftype in findings:
        is_bad = ftype and ftype not in DATE_SAFE
        if not verbose and not is_bad:
            continue
        mark = "DEFECT" if is_bad else ("UNRESOLVED" if ftype is None else "ok    ")
        print(f"  {mark} {rel}:{lineno}  {path} -> {ftype or '<未解析>'}")

    print(f"\n[mapper-date-param] 缺陷 {len(defects)} 处，未解析 {len(unresolved)} 处")
    if unresolved:
        # 解析器失效会伪装成「0 缺陷」，必须显式提醒，否则门禁是假的。
        print("[mapper-date-param] 警告：存在未解析项，判定不完整，请检查 namespace/方法名匹配")
    return 1 if defects else 0


if __name__ == "__main__":
    sys.exit(main())
