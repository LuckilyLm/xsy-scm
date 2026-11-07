/**
 * 读取后台为该分类配置的图片，不按名称推断图片或继承父级图标。
 * @param {object|null} category 正式分类节点；没有配置或为空时返回空串。
 * @returns {string} 文件服务解析的公开URL，空串由页面使用通用功能图标兜底。
 */
export function categoryIcon(category) {
  return category && typeof category === 'object' ? category.iconUrl || '' : '';
}
