import type {ProductId, ProductImportError, ProductImportResult} from '/@/types/business/scm/product';

/** 批量导入的前端辅助：只负责分组展示和预览匹配，写库判断都在后端。 */

/** 按 Excel 行号归并逐列错误，行号升序。 */
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
      group = {rowNumber: error.rowNumber, spuCode: error.spuCode, cells: []};
      byRow.set(error.rowNumber, group);
    }
    group.cells.push({column: error.column, code: error.code, message: error.message});
  }
  return [...byRow.values()].sort((a, b) => a.rowNumber - b.rowNumber);
}

/** totalErrors > 0 时整批没有写入，页面要明确提示。 */
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

/** 按「文件名去掉扩展名 == SPU 编码」精确匹配，不区分大小写；多个或零个命中都要显示给用户。 */
export function matchFilesBySpuCode(files: UploadedImageFile[], products: SpuTarget[]): FileMatchResult[] {
  const byCode = new Map<string, SpuTarget[]>();
  for (const product of products) {
    const key = product.spuCode.trim().toLowerCase();
    const list = byCode.get(key);
    if (list) list.push(product);
    else byCode.set(key, [product]);
  }
  return files.map((file) => {
    const code = file.fileName
      .replace(/\.[^.]+$/, '')
      .trim()
      .toLowerCase();
    const candidates = code ? byCode.get(code) ?? [] : [];
    const status: FileMatchStatus = candidates.length === 1 ? 'matched' : candidates.length > 1 ? 'ambiguous' : 'unmatched';
    return {file, status, candidates};
  });
}
