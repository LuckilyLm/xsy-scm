/* 客户域错误码 → 用户可读文案。码值定义见后端 `CustomerErrorCode` / `ScmCommonErrorCode`。 */
export function customerError(error: unknown): string {
  const response = error as {data?: {code?: number; msg?: string}; message?: string};
  const code = response?.data?.code;
  if (code === 40921) {
    return '数据已被其他人修改，请刷新后重试';
  }
  if (code === 40032) {
    return '上级客户不正确：不能选择自己或形成环形关系';
  }
  if (code === 40936) {
    return '客户编码已存在';
  }
  if (code === 40939) {
    return '客户已被业务数据引用，不能删除';
  }
  // 40930 的文案由后端按客户实际状态生成（黑名单 / 暂停合作），这里不要覆盖。
  return response?.data?.msg || response?.message || '操作失败，请重试';
}
