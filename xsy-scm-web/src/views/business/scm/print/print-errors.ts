/**
 * 打印域错误码 → 可执行的中文提示。
 *
 * 取向与采购域一致：提示要说「下一步做什么」，不是复述错误码名字。
 * 例如 41303 不是「模板在用」，而是「先把别的模板设为默认」——
 * 因为删除默认模板会让该类型的打印失去默认入口，用户唯一的出路就是换个默认。
 *
 * 未登记的码回落到后端的 `msg`，再回落到通用文案；不吞掉后端消息。
 */
type BusinessError = { code?: number; msg?: string; message?: string };

const MESSAGES: Record<number, string> = {
    41301: '打印模板不存在或已被删除，请刷新后重试',
    41302: '同类型下已有相同编码的模板，请换一个编码',
    41303: '默认模板不能删除：请先把别的模板设为默认',
    41304: '该单据类型暂不支持可配置打印',
    41305: '模板内容不合法：请检查标题、纸张与字段选择',
    41306: '模板包含该单据类型不支持的字段，请重新选择',
    41307: '打印模板至少要选择一列明细字段',
    41308: '80mm 小票不支持横向，请改用纵向或 A4 纸',
    41309: '标题与页脚备注只能是纯文本，不能包含尖括号',
    41310: '打印记录不存在或已被清理',
};

export function printError(error: unknown): string {
    const raw = error as BusinessError & { data?: BusinessError; response?: { data?: BusinessError } };
    const e = raw?.response?.data ?? raw?.data ?? raw;
    const code = e?.code;
    if (typeof code === 'number' && MESSAGES[code]) {
        return MESSAGES[code];
    }
    return e?.msg ?? e?.message ?? '操作失败，请重试';
}

export default printError;
