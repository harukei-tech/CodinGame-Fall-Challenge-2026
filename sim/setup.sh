#!/usr/bin/env bash
# 審判のソースを取得する（固定コミット）。Maven のビルドは referee コンテナの中で行う。
set -euo pipefail
cd "$(dirname "$0")"
COMMIT=b691312
if [[ ! -d ref ]]; then
  git clone -q https://github.com/eulerscheZahl/TryAngle-Catch ref
fi
git -C ref checkout -q "$COMMIT"
# 審判の単体テストは対戦に不要で、依存関係の不足でビルドが失敗するので除外する。
rm -f ref/src/test/java/engine/BoardTest.java
cp harness/Arena.java ref/src/test/java/Arena.java
