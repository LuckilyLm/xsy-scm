#!/bin/sh
set -eu
# Bucket name enters a policy document: accept only S3-compatible name characters.
case "$XSY_FILE_BUCKET" in
  ''|*[!a-z0-9.-]*) echo "Invalid bucket name" >&2; exit 1 ;;
esac
mc alias set f0 http://minio:9000 "$XSY_FILE_ACCESS_KEY" "$XSY_FILE_SECRET_KEY" >/dev/null
mc mb --ignore-existing "f0/$XSY_FILE_BUCKET"
cat > /tmp/f0-public-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"AWS": ["*"]},
    "Action": ["s3:GetObject"],
    "Resource": ["arn:aws:s3:::$XSY_FILE_BUCKET/public/*"]
  }]
}
EOF
# Replaces the entire anonymous policy; no ListBucket, PutObject or private/* grant.
mc anonymous set-json /tmp/f0-public-policy.json "f0/$XSY_FILE_BUCKET"
echo "F0 bucket policy configured: anonymous GET for public/* only"
