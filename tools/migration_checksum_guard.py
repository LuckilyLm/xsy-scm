#!/usr/bin/env python3
"""已应用 Flyway 迁移的字节快照守卫。Exit 0 = 一致, 1 = 漂移。

Flyway 的 validate 比较的是 `flyway_schema_history.checksum`，而该值只由文件字节决定，
与 SQL 语义无关：把迁移重新排版（换行合并、缩进、缩进字符）就会改变它，
于是任何**已经建过库**的环境启动即 `FlywayValidateException`，全部 `*PgIT` 在容器加载期失败。
本守卫在没有数据库的场合（提交前、CI、纯代码审阅）先把这条不变量钉住。

校验和按 Flyway 11 的实现复刻：`BufferedReader.readLine()` 逐行取内容（分隔符只认
`\\r\\n` / `\\r` / `\\n`），去掉首行 BOM，按 UTF-8 做 CRC32，最后 `long → int` 截断为**有符号** 32 位。
行数与换行风格都不进结果，因此 LF 与 CRLF 的同内容文件校验和相同。
"""
from __future__ import annotations

import argparse
import binascii
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "xsy-scm-server/sa-admin/src/main/resources/db/migration"
SNAPSHOT_PATH = ROOT / "tools/migration_checksum_snapshot.json"
ALGORITHM = "flyway-crc32-readline-v11"
FILENAME_RE = re.compile(r"^V(?P<version>[0-9][0-9.]*)__(?P<description>.+)\.sql$")


def flyway_checksum(raw: bytes) -> int:
    """复刻 Flyway {@code ChecksumCalculator.calculateChecksumForResource}。"""
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ValueError(f"迁移文件不是合法 UTF-8：{exc}") from exc
    if text.startswith("\ufeff"):
        text = text[1:]
    value = 0
    for line in re.split(r"\r\n|\r|\n", text):
        # readLine() 不会产出末尾空行，而 CRC32 更新空字节序列本就恒等，故无需特判。
        value = binascii.crc32(line.encode("utf-8"), value)
    return value - (1 << 32) if value >= (1 << 31) else value


def version_key(version: str) -> tuple:
    return tuple(int(part) for part in version.split("."))


def scan_migrations(directory: Path) -> dict:
    """version -> {file, checksum}；同一版本号出现两次直接报错，因为 Flyway 会拒绝启动。"""
    migrations: dict[str, dict] = {}
    for path in sorted(directory.glob("V*.sql")):
        match = FILENAME_RE.match(path.name)
        if not match:
            raise ValueError(f"迁移文件名不符合 V<version>__<description>.sql：{path.name}")
        version = match.group("version")
        entry = {"file": path.name, "checksum": flyway_checksum(path.read_bytes())}
        if version in migrations:
            raise ValueError(f"版本号 {version} 重复：{migrations[version]['file']} 与 {path.name}")
        migrations[version] = entry
    return migrations


def load_snapshot(path: Path, allow_missing: bool = False) -> dict:
    if not path.exists():
        if allow_missing:
            return {"algorithm": ALGORITHM, "migrations": {}}
        raise ValueError(f"快照不存在：{path}；先用 `sync` 冻结当前已应用迁移")
    snapshot = json.loads(path.read_text(encoding="utf-8"))
    if snapshot.get("algorithm") != ALGORITHM:
        raise ValueError(f"快照算法标记为 {snapshot.get('algorithm')!r}，与当前 {ALGORITHM!r} 不符")
    return snapshot


def compare(snapshot: dict, current: dict) -> dict:
    """把快照与当前字节分成四类，供 check 判定、sync 复用。"""
    snap = snapshot["migrations"]
    drift, vanished, renamed, pending = [], [], [], []
    for version in sorted(snap, key=version_key):
        recorded, now = snap[version], current.get(version)
        if now is None:
            vanished.append(version)
        elif now["checksum"] != recorded["checksum"]:
            drift.append((version, recorded["file"], recorded["checksum"], now["checksum"]))
        elif now["file"] != recorded["file"]:
            renamed.append((version, recorded["file"], now["file"]))
    pending = [v for v in sorted(current, key=version_key) if v not in snap]
    return {"drift": drift, "vanished": vanished, "renamed": renamed, "pending": pending}


def write_snapshot(path: Path, migrations: dict) -> None:
    ordered = {v: migrations[v] for v in sorted(migrations, key=version_key)}
    path.write_text(json.dumps({"algorithm": ALGORITHM, "migrations": ordered},
                               ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cmd_check(args) -> int:
    current = scan_migrations(Path(args.migration_dir))
    snapshot = load_snapshot(Path(args.snapshot))
    result = compare(snapshot, current)

    print("=== Migration checksum guard ===")
    print(f"migrations   : {len(current)}")
    print(f"drift        : {len(result['drift'])}  (must be 0)")
    print(f"missing      : {len(result['vanished'])}  (must be 0)")
    print(f"renamed      : {len(result['renamed'])}")
    print(f"unbaked      : {len(result['pending'])}")

    if result["drift"]:
        print("\n--- 已应用迁移的字节被改动 (blocking) ---")
        for version, name, was, now in result["drift"]:
            print(f"  V{version} {name}: snapshot {was} -> current {now}")
        print("  恢复原字节，不要改快照：格式化后的 SQL 语义没变但校验和变了，"
              "真实库上的 flyway.validate() 会失败。")

    if result["vanished"]:
        print("\n--- 快照里的版本号在文件里消失 (blocking) ---")
        for version in result["vanished"]:
            print(f"  V{version} {snapshot['migrations'][version]['file']}")
        print("  已应用的版本号不能改号或删文件；确需重排时按「保留已被真实库应用的版本号」处理并同步快照。")

    if result["renamed"]:
        print("\n--- 文件名变了而字节没变（通常无害）---")
        for version, was, now in result["renamed"]:
            print(f"  V{version}: {was} -> {now}")

    if result["pending"]:
        print("\n--- 未入快照的新迁移 ---")
        print("  " + ", ".join(f"V{v}" for v in result["pending"]))
        print("  在真实库应用后用 `sync` 冻结；本状态不算失败。")

    ok = not result["drift"] and not result["vanished"]
    print("\nRESULT:", "PASS" if ok else "FAIL")
    return 0 if ok else 1


def cmd_sync(args) -> int:
    snapshot_path = Path(args.snapshot)
    snapshot = load_snapshot(snapshot_path, allow_missing=True)
    current = scan_migrations(Path(args.migration_dir))
    result = compare(snapshot, current)

    if result["drift"] and not args.force:
        print("=== Migration checksum guard ===")
        print("拒绝写入：以下已冻结版本的字节与快照不一致，先恢复原文件再 sync。")
        for version, name, was, now in result["drift"]:
            print(f"  V{version} {name}: snapshot {was} -> current {now}")
        print("\nRESULT: FAIL")
        return 1
    if result["vanished"] and not args.prune:
        print("=== Migration checksum guard ===")
        print("拒绝写入：快照里的版本号在文件里不存在（"
              + ", ".join(f"V{v}" for v in result["vanished"])
              + "）。确认要丢弃这些条目时加 --prune。")
        print("\nRESULT: FAIL")
        return 1

    merged = dict(snapshot["migrations"])
    if args.prune:
        merged = {v: e for v, e in merged.items() if v in current}
    added = [v for v in result["pending"]] + ([v for v, *_ in result["drift"]] if args.force else [])
    for version in result["pending"]:
        merged[version] = current[version]
    if args.force:
        for version, _, _, _ in result["drift"]:
            merged[version] = current[version]

    write_snapshot(snapshot_path, merged)
    print("=== Migration checksum guard ===")
    print(f"frozen       : {len(merged)}")
    print("added        : " + (", ".join(f"V{v}" for v in sorted(added, key=version_key)) or "none"))
    if args.force and result["drift"]:
        print("OVERWRITTEN  : " + ", ".join(f"V{v}" for v, *_ in result["drift"])
              + "  ← 用 --force 覆盖了已冻结校验和，必须能说明为什么旧字节不可能再出现在任何库里")
    print("\nRESULT: PASS")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--migration-dir", default=str(MIGRATION_DIR))
    parser.add_argument("--snapshot", default=str(SNAPSHOT_PATH))
    sub = parser.add_subparsers(dest="mode", required=True)
    sub.add_parser("check", help="比对快照与当前迁移字节").set_defaults(func=cmd_check)
    ps = sub.add_parser("sync", help="把新迁移的当前字节冻结进快照")
    ps.add_argument("--force", action="store_true", help="覆盖已冻结版本的校验和")
    ps.add_argument("--prune", action="store_true", help="删除文件里已不存在的版本条目")
    ps.set_defaults(func=cmd_sync)
    args = parser.parse_args()
    try:
        return args.func(args)
    except ValueError as exc:
        print(f"=== Migration checksum guard ===\n{exc}\n\nRESULT: FAIL")
        return 1


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        # 说明里有中文，Windows 默认 GBK 控制台会直接抛 UnicodeEncodeError。
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
