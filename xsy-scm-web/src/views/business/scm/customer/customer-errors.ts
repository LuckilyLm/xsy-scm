/* 客户域错误码 → 用户可读文案。码值定义见后端 `CustomerErrorCode` / `ScmCommonErrorCode`。 */
export function customerError(error: unknown): string {
    const response = error as { data?: { code?: number; msg?: string }; message?: string };
    const code = response?.data?.code;
    if (code === 40921) {
        return '数据已被其他人修改，请刷新后重试';
    }
    if (code === 40032) {
        return '上级客户不正确：只有集团客户可以作为上级，且不能形成环';
    }
    if (code === 40936) {
        return '客户编码已存在';
    }
    if (code === 40937) {
        return '客户类型编码已存在';
    }
    if (code === 40938) {
        return '该客户类型已被客户引用，不能删除';
    }
    if (code === 40939) {
        return '客户已被业务数据引用，不能删除';
    }
    if (code === 40431) {
        return '客户类型不存在或已停用';
    }
    if (code === 40930) {
        return '客户当前状态不可交易';
    }
    return response?.data?.msg || response?.message || '操作失败，请重试';
}
