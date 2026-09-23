#!/usr/bin/env python3
"""把商品「新增导入」模板填成可提交的新增件（仅供 E2E / 手工验收使用）。

为什么需要：Wave 1 的浏览器验收要求真实跑一遍「下载新增模板 → 填一行多 SKU → 页面以新增模式上传 →
列表见新商品」，而前端不解析 xlsx（导入是整文件上传给后端校验），所以待上传文件必须由测试侧生成。

做法与约束（对齐 `ProductImportService` 的新增读法）：
  · 输入只能是 `GET /scm/product/import/template?mode=CREATE`：新增与更新是**不同列集合**
    （更新模板多四列定位键），喂错模板应由这里显性报错，而不是等后端回 HEADER_INVALID；
  · 每个 `--row` 生成一个数据行，同一 SPU 的多行即「一个商品多个 SKU」，这是新增模式独有的组织方式；
    后端要求同一 SPU 各行的 商品名称 / 分类编码 / 商品上下架 / 储存方式 / 标签编码 完全一致，
    所以这些 SPU 级列要在每一行都重复给出；
  · 未被任何 `--row` 提到的列一律留空：新增语义里的空白就是「不提供该字段」，与更新语义的
    「空白=保持原值」相反，因此这里没有 `--blank`；
  · 「模板版本」取模板自带值、拒绝调用方改写，测试不硬编码服务端常量；
  · 只按表头名取列，不按下标写死：列顺序变化应由表头对不上而显性报错，而不是静默改错列；
  · 值以字符串写入，避免 openpyxl 把数字转成数值单元格后与后端的文本正则不一致。

用法：
    python tools/patch_product_create_xlsx.py --in template.xlsx --out create.xlsx \
        --row "SPU编码=SPU0001;商品名称=示例蔬菜;分类编码=FRESH;商品上下架=ON_SHELF;SKU编码=SKU0001;规格名称=500g/份;销售单位=份;商品类型=STANDARD;市场价=9.9000;SKU上下架=ON_SHELF;默认SKU=是" \
        --row "SPU编码=SPU0001;SKU编码=SKU0002;商品名称=示例蔬菜;分类编码=FRESH;商品上下架=ON_SHELF;规格名称=1kg/箱;销售单位=箱;商品类型=STANDARD;市场价=18.8000;SKU上下架=ON_SHELF;默认SKU=否"
输出（stdout，单行 JSON）：
    {"rows": 2, "templateVersion": "1.0"}
"""

import argparse
import json
import sys

import openpyxl

TEMPLATE_VERSION_COLUMN = "模板版本"
# 更新模板独有：出现在新增输入里说明下载时选错了模式。
UPDATE_ONLY_COLUMNS = ("SPU ID", "SPU版本", "SKU ID", "SKU版本")


class InputError(Exception):
    """调用方传参或输入文件不符合契约；主流程统一转成退出码 1。"""


def read_headers(sheet) -> dict:
    headers = {}
    for cell in sheet[1]:
        if cell.value is not None:
            headers[str(cell.value).strip()] = cell.column
    return headers


def parse_row(spec: str) -> dict:
    edits = {}
    for pair in spec.split(";"):
        if not pair.strip():
            continue
        if "=" not in pair:
            raise InputError(f"--row 必须是 列名=值;列名=值：{pair}")
        column, value = pair.split("=", 1)
        edits[column.strip()] = value
    if not edits:
        raise InputError("--row 至少要有一个 列名=值")
    if TEMPLATE_VERSION_COLUMN in edits:
        raise InputError(f"「{TEMPLATE_VERSION_COLUMN}」保留模板自带值，不能由调用方改写")
    return edits


def build_rows(sheet, headers, rows) -> str:
    missing = [name for name in headers if name in UPDATE_ONLY_COLUMNS]
    if missing:
        raise InputError(f"输入含更新定位键列（{', '.join(missing)}），那是 mode=UPDATE 的模板")
    unknown = sorted({name for row in rows for name in row} - set(headers))
    if unknown:
        raise InputError(f"表头里没有这些列：{', '.join(unknown)}")
    version_column = headers[TEMPLATE_VERSION_COLUMN]
    # 版本串由服务端生成，调用方不硬编码；缺失即说明输入不是可用模板。
    template_version = sheet.cell(row=2, column=version_column).value
    if template_version in (None, ""):
        raise InputError(f"模板第 2 行的「{TEMPLATE_VERSION_COLUMN}」为空，无法生成新增文件")
    # 样例行的编码、分类、单价一旦被当成真实数据提交，会静默造出一条脏商品，所以整行重建。
    for index, row in enumerate(rows):
        number = 2 + index
        for column in headers.values():
            sheet.cell(row=number, column=column).value = None
        for name, value in row.items():
            sheet.cell(row=number, column=headers[name]).value = value
        sheet.cell(row=number, column=version_column).value = str(template_version)
    return str(template_version)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    parser.add_argument("--row", dest="row_specs", action="append", default=[], metavar="列名=值;列名=值")
    args = parser.parse_args()

    try:
        if not args.row_specs:
            raise InputError("至少要给一个 --row")
        rows = [parse_row(spec) for spec in args.row_specs]
        book = openpyxl.load_workbook(args.src)
        sheet = book.active
        version = build_rows(sheet, read_headers(sheet), rows)
        book.save(args.dst)
    except InputError as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps({"rows": len(rows), "templateVersion": version}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    # 调用方（E2E）按 UTF-8 解 stdout JSON，而 Windows 默认控制台是 GBK：中文列名会被编成
    # GBK 字节再按 UTF-8 解码，键名对不上等于静默丢数据，所以在这里钉死输出编码。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
