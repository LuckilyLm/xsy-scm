import type { ProductId, ProductImportError, ProductImportResult } from '/@/types/business/scm/product';

/**
 * PCO-2 批量导入的纯前端辅助：只做「展示分组」和「预览匹配」，不做任何写库判断。
 * 导入的正确性由后端保证（0 错误才写、写失败整批回滚），这里仅把后端结果映射成可渲染结构。
 */

/** 把逐列错误按 Excel 行号归并，供「一行商品的多条错误一起展示」；行号升序，行内保持后端给出顺序。 */
export interface ImportRowGroup {
    rowNumber: number;
    spuCode: string | null;
    cells: Pick<ProductImportError, 'column' | 'code' | 'message'>[];
}

export function groupErrorsByRow(errors: ProductImportError[]): ImportRowGroup[] {
    const byRow = new Map<number, ImportRowGroup>();
    for (const error of errors) {
        let group = byRow.get(error.rowNumber);
        if (!group) {
            group = { rowNumber: error.rowNumber, spuCode: error.spuCode, cells: [] };
            byRow.set(error.rowNumber, group);
        }
        group.cells.push({ column: error.column, code: error.code, message: error.message });
    }
    return [...byRow.values()].sort((a, b) => a.rowNumber - b.rowNumber);
}

/** totalErrors 是后端权威门控；为真时本次没有任何商品落库，页面必须显性说明「整批未导入」。 */
export function hasBlockingErrors(result: ProductImportResult): boolean {
    return result.totalErrors > 0;
}

/** 一个已上传文件名 → 目标商品的匹配状态，供图片中心「先预览再确认」。 */
export type FileMatchStatus = 'matched' | 'ambiguous' | 'unmatched';

export interface UploadedImageFile {
    fileName: string;
    fileKey: string;
}

export interface SpuTarget {
    spuId: ProductId;
    spuCode: string;
}

export interface FileMatchResult {
    file: UploadedImageFile;
    status: FileMatchStatus;
    /** matched 时唯一的商品；ambiguous 时全部候选；unmatched 时为空。 */
    candidates: SpuTarget[];
}

/**
 * 按「文件名去扩展名 == SPU 编码」做不区分大小写的精确匹配。
 * 命中多个（编码理论上唯一，仍防脏数据）判为歧义，命中零个判为未匹配；
 * 两者都必须显性回给用户，绝不能像后端回滚那样静默丢弃。
 */
export function matchFilesBySpuCode(files: UploadedImageFile[], products: SpuTarget[]): FileMatchResult[] {
    const byCode = new Map<string, SpuTarget[]>();
    for (const product of products) {
        const key = product.spuCode.trim().toLowerCase();
        const list = byCode.get(key);
        if (list) list.push(product);
        else byCode.set(key, [product]);
    }
    return files.map((file) => {
        const code = file.fileName.replace(/\.[^.]+$/, '').trim().toLowerCase();
        const candidates = code ? byCode.get(code) ?? [] : [];
        const status: FileMatchStatus = candidates.length === 1 ? 'matched' : candidates.length > 1 ? 'ambiguous' : 'unmatched';
        return { file, status, candidates };
    });
}
