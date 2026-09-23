#!/usr/bin/env python3
"""生成商品「更新导入」用的 xlsx（仅供 E2E / 手工验收使用）。

为什么需要：Wave 1 的浏览器验收要真实跑一遍「以更新模式上传 → 页面见新值 → 旧文件重放被拒」，
而前端不解析 xlsx（导入是整文件上传给后端校验），所以待上传文件必须由测试侧生成。

两种输入（普通列表导出与更新模板是**不同列集合**：导出含分类路径与标签名称供人看，模板要分类编码与标签编码）：
  · `--template`：输入 `GET /scm/product/import/template?mode=UPDATE`，把第 2 行占位样例换成真实定位键；
  · 默认：输入列表导出文件，只改业务列。

做法与约束（对齐 `ProductImportService` 的更新读法）：
  · 默认模式下定位键四列（SPU ID / SPU版本 / SKU ID / SKU版本）**永不改动**——它们既是行的定位键，
    也是乐观锁版本，重写它等于伪造并发基线；
  · `--template` 模式是唯一例外：模板自带样例 ID 与库里无关，必须换成调用方从详情接口取到的真实 ID 与版本；
    该行未被 `--set` 指定的列一律清空（空白=保持原值，正是更新语义），「模板版本」保留模板自带值，
    让测试不硬编码服务端常量；
  · 只按表头名取列，不按下标写死：列顺序变化应由表头对不上而显性报错，而不是静默改错列；
  · `--blank` 用来证明「空白列保持原值」这条更新语义（不是抹掉），被清空的列原值回写到 JSON 里，
    由调用方去库里断言原值仍在；
  · 值以字符串写入，避免 openpyxl 把数字/日期转成数值单元格后与后端的文本正则不一致。

用法：
    python tools/patch_product_update_xlsx.py --template --in template.xlsx --out update.xlsx \
        --set "SPU ID=123" --set SPU版本=0 --set "SKU ID=456" --set SKU版本=0 --set 别名=新别名
    python tools/patch_product_update_xlsx.py --in export.xlsx --out update.xlsx \
        --set 别名=新别名 --blank 品牌
输出（stdout，单行 JSON）：
    {"rows": 1, "template": true, "sets": {"别名": "新别名"}, "templateVersion": "1.0"}
    {"rows": 2, "sets": {"别名": "新别名"}, "originals": {"别名": "旧值", "品牌": "旧品牌"}}
"""

import argparse
import json
import sys

import openpyxl

LOCATION_KEYS = ("SPU ID", "SPU版本", "SKU ID", "SKU版本")
TEMPLATE_VERSION_COLUMN = "模板版本"


class InputError(Exception):
    """调用方传参或输入文件不符合契约；主流程统一转成退出码 1。"""


def parse_edits(args) -> dict:
    edits = {}
    for item in args.sets:
        if "=" not in item:
            raise InputError(f"--set 必须是 列名=值：{item}")
        column, value = item.split("=", 1)
        edits[column.strip()] = value
    for column in args.blanks:
        edits[column.strip()] = None
    if not edits:
        raise InputError("至少要给一个 --set 或 --blank")
    touched = set(edits) & set(LOCATION_KEYS)
    if touched and not args.template:
        raise InputError(f"定位键列不可改写：{', '.join(sorted(touched))}")
    return edits


def read_headers(sheet) -> dict:
    headers = {}
    for cell in sheet[1]:
        if cell.value is not None:
            headers[str(cell.value).strip()] = cell.column
    return headers


def check_columns(headers, edits, template_mode: bool) -> None:
    missing = [name for name in edits if name not in headers]
    if missing:
        raise InputError(f"表头里没有这些列：{', '.join(missing)}")
    if template_mode and TEMPLATE_VERSION_COLUMN not in headers:
        raise InputError(f"更新模板缺少「{TEMPLATE_VERSION_COLUMN}」列，文件不可用")


def rewrite_template_row(sheet, headers, edits) -> str:
    version_column = headers[TEMPLATE_VERSION_COLUMN]
    # 版本串由服务端生成，调用方不硬编码；缺失即说明输入不是可用模板。
    template_version = sheet.cell(row=2, column=version_column).value
    if template_version in (None, ""):
        raise InputError(f"模板第 2 行的「{TEMPLATE_VERSION_COLUMN}」为空，无法生成更新文件")
    # 样例行的 SPU编码/名称/单位等一旦被当成真实数据提交，会静默覆盖线上字段，所以整行重建。
    for column in headers.values():
        sheet.cell(row=2, column=column).value = None
    for name, value in edits.items():
        if name == TEMPLATE_VERSION_COLUMN:
            raise InputError(f"「{name}」保留模板自带值，不能由调用方改写")
        sheet.cell(row=2, column=headers[name]).value = value
    sheet.cell(row=2, column=version_column).value = str(template_version)
    return str(template_version)


def rewrite_export_rows(sheet, headers, edits) -> int:
    rows = 0
    for row in range(2, sheet.max_row + 1):
        if all(sheet.cell(row=row, column=col).value in (None, "") for col in headers.values()):
            continue    # 导出末尾可能留有空行，空行不参与更新也不计数
        for name, value in edits.items():
            sheet.cell(row=row, column=headers[name]).value = value
        rows += 1
    if rows == 0:
        raise InputError("导出文件里没有数据行，无法生成更新文件")
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    parser.add_argument("--set", dest="sets", action="append", default=[], metavar="列名=值")
    parser.add_argument("--blank", dest="blanks", action="append", default=[], metavar="列名")
    parser.add_argument("--template", action="store_true",
                        help="输入是更新模板：第 2 行样例换成真实定位键，未指定的列清空（=保持原值）")
    args = parser.parse_args()

    try:
        edits = parse_edits(args)
        book = openpyxl.load_workbook(args.src)
        sheet = book.active
        headers = read_headers(sheet)
        check_columns(headers, edits, args.template)
        if args.template:
            payload = {"rows": 1, "template": True,
                       "sets": {k: v for k, v in edits.items() if v is not None},
                       "templateVersion": rewrite_template_row(sheet, headers, edits)}
        else:
            originals = {name: sheet.cell(row=2, column=headers[name]).value for name in edits}
            payload = {"rows": rewrite_export_rows(sheet, headers, edits),
                       "sets": {k: v for k, v in edits.items() if v is not None},
                       "originals": originals}
        book.save(args.dst)
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps(payload, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    # 调用方（E2E）按 UTF-8 解 stdout JSON，而 Windows 默认控制台是 GBK：中文列名会被编成
    # GBK 字节再按 UTF-8 解码，键名对不上等于静默丢数据，所以在这里钉死输出编码。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
