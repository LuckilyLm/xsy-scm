import { mallMedia } from '@/utils/mall-media';
/* 分类导航使用MinIO中的原始交付PNG，名称不同但语义相同的分类共用素材。 */
const ROOT = mallMedia('mall-visual/category/');
const CATEGORY_IMAGES = [
  [/肉禽蛋品|肉禽蛋/, 'meat-eggs'],
  [/生鲜蔬菜|^蔬菜$|^果蔬$/, 'vegetables'],
  [/生鲜水果|^水果$|国产水果|进口水果|苹果|柑|橘|橙|葡萄|车厘子|樱桃|榴莲|香蕉|时令|鲜果/, 'fruit'],
  [/菌|菇/, 'mushrooms'],
  [/叶菜|青菜|白菜|花菜|西兰花/, 'leafy'],
  [/根茎|萝卜|土豆|薯|葱|蒜|姜/, 'roots'],
  [/瓜|茄/, 'gourds'],
  [/牛|羊/, 'beef-lamb'],
  [/猪/, 'pork'],
  [/^蛋类$|蛋品/, 'eggs'],
  [/禽|鸡|鸭/, 'poultry'],
  [/活鲜|冰鲜|水产/, 'fish-shrimp'],
  [/冻品/, 'frozen'],
  [/鱼虾/, 'fish-shrimp'],
  [/虾|蟹/, 'shrimp-crab'],
  [/鱼/, 'fish-shrimp'],
  [/食用油/, 'cooking-oil'],
  [/米|面/, 'rice-flour'],
  [/杂粮|豆|干货/, 'grains'],
  [/粮|油/, 'grain-oil'],
  [/香辛/, 'spices'],
  [/调味/, 'seasoning'],
];

/**
 * 返回现有分类对应的PNG；未知名称可按父级回退，不推断或创建业务分类。
 * @param {string|null} name 分类名称；为空时尝试父级名称。
 * @param {string|null} parentName 父级名称；缺省或仍无法匹配时返回空串，由界面展示通用图标。
 * @returns {string} 公开图片URL；未配置媒体或无法匹配时返回空串。
 */
export function categoryIcon(name, parentName) {
  const image =
    CATEGORY_IMAGES.find(([pattern]) => pattern.test(name || ''))?.[1] || CATEGORY_IMAGES.find(([pattern]) => pattern.test(parentName || ''))?.[1];
  return image && ROOT ? ROOT + image + '.png' : '';
}
