"""将media中的原始PNG发布到MinIO不可变目录，不向小程序写入存储凭据。"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen

from minio import Minio
from minio.error import S3Error


def main():
    """校验清单、上传缺少的对象并匿名核对内容；失败时退出，不覆盖已有图片。"""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--endpoint', required=True, help='MinIO服务地址，包含http或https')
    parser.add_argument('--bucket', required=True, help='已有的图片桶')
    parser.add_argument('--public-url-prefix', required=True, help='公开图片桶URL，含桶路径')
    parser.add_argument('--report', help='可选的本地验证记录JSON路径')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent / 'media'
    manifest = json.loads((root / 'manifest.json').read_text(encoding='utf-8'))
    files = [{'file': p.relative_to(root).as_posix(), 'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'size': p.stat().st_size}
             for p in sorted(root.rglob('*.png'))]
    digest = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()[:16]
    if files != manifest['files'] or digest != manifest['digest']:
        raise SystemExit('原图与media/manifest.json不一致，请先核对来源并更新清单')
    endpoint = urlparse(args.endpoint)
    public = urlparse(args.public_url_prefix)
    if (endpoint.scheme not in ('http', 'https') or not endpoint.hostname
            or endpoint.username or endpoint.password or endpoint.path not in ('', '/') or endpoint.query or endpoint.fragment
            or public.scheme not in ('http', 'https') or not public.hostname
            or public.username or public.password or public.query or public.fragment):
        raise SystemExit('MinIO服务及公开URL配置不正确')
    access = os.environ.get('XSY_FILE_ACCESS_KEY')
    secret = os.environ.get('XSY_FILE_SECRET_KEY')
    if not access or not secret:
        raise SystemExit('请通过环境提供XSY_FILE_ACCESS_KEY与XSY_FILE_SECRET_KEY')
    client = Minio(endpoint.netloc, access_key=access, secret_key=secret, secure=endpoint.scheme == 'https')
    prefix = f'public/image/mall-ui/{digest}'
    base = args.public_url_prefix.rstrip('/') + '/' + prefix
    uploaded = 0
    for item in files:
        key = prefix + '/' + item['file']
        try:
            existing = client.stat_object(args.bucket, key)
            if existing.metadata.get('x-amz-meta-sha256') != item['sha256']:
                raise SystemExit('已存在对象与原图不一致，停止上传：' + item['file'])
        except S3Error as error:
            if error.code not in ('NoSuchKey', 'NoSuchObject'):
                raise
            content = (root / item['file']).read_bytes()
            client.put_object(args.bucket, key, io.BytesIO(content), len(content), content_type='image/png',
                              metadata={'sha256': item['sha256'], 'Cache-Control': 'public, max-age=31536000, immutable'})
            uploaded += 1
        # 不带存储认证读取，证明小程序能直接获取原图，而不只是管理员能访问。
        with urlopen(base + '/' + item['file'], timeout=20) as response:
            if response.status != 200 or hashlib.sha256(response.read()).hexdigest() != item['sha256']:
                raise SystemExit('公开图片校验失败：' + item['file'])
    report = {'mediaUrl': base, 'files': len(files), 'bytes': sum(item['size'] for item in files), 'uploaded': uploaded, 'anonymousHashVerified': True}
    if args.report:
        Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except S3Error as error:
        raise SystemExit('MinIO请求失败：' + error.code) from None
