#!/usr/bin/env bash
# 本番のボスと固定シード10個で対戦し、勝敗とT20の差、包囲とATTACKの損得を出す。
# 使い方: tools/seeds.sh <Player.java> [結果ファイル]
cd "$(dirname "$0")/.."
BOT=${1:-bot/Player.java}; OUT=${2:-/dev/null}
ids=()
for i in $(seq 1 10); do
  seed=$((1000 + i * 7919))
  line=$(BOT=$BOT OPTIONS="seed=$seed" tools/play.sh 1 boss 2>&1 | grep -E '^game' | cut -c1-60)
  if [[ -z "$line" ]]; then echo "seed=$seed: could not start, stopping"; break; fi
  echo "seed=$seed $line" >> "$OUT"
  ids+=("$(sed -E 's/game ([0-9]+):.*/\1/' <<<"$line")")
  sleep "${SPACING:-20}"
done
python3 sim/liveresult.py "${ids[@]}"
