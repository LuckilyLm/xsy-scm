export function productError(error: unknown): string {
  const response = error as { data?: { code?: number; msg?: string }; message?: string };
  if (response?.data?.code === 40921) return '数据已被其他人修改，请关闭后重新打开，刷新详情后重试';
  return response?.data?.msg || response?.message || '加载失败，请重试';
}
