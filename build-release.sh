#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_DIR="${PROJECT_DIR}/release"
VERSION="${1:-$(date '+%Y%m%d-%H%M%S')}"
ARCHIVE="${RELEASE_DIR}/words-images-${VERSION}.tar.gz"
CHECKSUM="${ARCHIVE}.sha256"

if [[ ! "${VERSION}" =~ ^[a-zA-Z0-9._-]+$ ]]; then
    echo "版本号只能包含字母、数字、点、下划线和连字符。" >&2
    exit 1
fi

cd "${PROJECT_DIR}"
mkdir -p "${RELEASE_DIR}"

echo "正在重新构建 words-app:latest 和 words-frontend:latest ..."
docker compose build --pull --no-cache app frontend

TEMP_ARCHIVE="$(mktemp "${RELEASE_DIR}/.words-images-${VERSION}.XXXXXX")"
trap 'rm -f "${TEMP_ARCHIVE}"' EXIT

echo "正在导出镜像到 ${ARCHIVE} ..."
docker image save words-app:latest words-frontend:latest | gzip -c > "${TEMP_ARCHIVE}"
mv "${TEMP_ARCHIVE}" "${ARCHIVE}"
chmod 0644 "${ARCHIVE}"
trap - EXIT

(
    cd "${RELEASE_DIR}"
    shasum -a 256 "$(basename "${ARCHIVE}")" > "$(basename "${CHECKSUM}")"
)

echo "发布包已生成："
echo "  ${ARCHIVE}"
echo "  ${CHECKSUM}"
echo "可使用以下命令导入："
echo "  docker load -i ${ARCHIVE}"
