/**
 * 商品/商城图片上传白名单。
 *
 * 与后端 SecurityFileService.IMAGE_MIME_TYPES 保持一致；SVG 可以内嵌脚本，不纳入白名单。
 * 前端校验只用于提前反馈，真正的类型判断由后端按文件内容识别 MIME，扩展名不作为依据。
 */
export const IMAGE_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp'];
export const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp'];
export const IMAGE_ACCEPT = '.jpg,.jpeg,.png,.webp,.gif,.bmp';

/** 与后端 level3_protect_config.maxUploadFileSizeMb 的默认值一致。 */
export const MAX_IMAGE_SIZE_MB = 20;

export function imageFileError(file: File): string | null {
  const name = (file.name || '').toLowerCase();
  const dot = name.lastIndexOf('.');
  const extension = dot >= 0 ? name.slice(dot + 1) : '';
  if (!IMAGE_EXTENSIONS.includes(extension)) {
    return '仅支持 JPG、PNG、WebP、GIF、BMP 格式';
  }
  const mime = (file.type || '').toLowerCase();
  if (mime && !IMAGE_MIME_TYPES.includes(mime)) {
    return '文件内容不是受支持的图片格式';
  }
  if (!file.size) {
    return '图片文件为空';
  }
  if (file.size > MAX_IMAGE_SIZE_MB * 1024 * 1024) {
    return `单张图片不能超过 ${MAX_IMAGE_SIZE_MB} MB`;
  }
  return null;
}
