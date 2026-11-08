#!/usr/bin/env bash
# 本番のボスと固定シード10個で対戦し、勝敗とT20の差、包囲とATTACKの損得を出す。
# 使い方: tools/seeds.sh <Player.java> [結果ファイル]
# COUNT でシードの数を変える（既定は10）。
# SEED_BASE を変えると別の10マップで対戦する。
# OPPONENT に agent_id を指定すると、ボス以外と対戦する。
cd "$(dirname "$0")/.."
BOT=${1:-bot/Player.java}; OUT=${2:-/dev/null}
ids=()
for i in $(seq 1 ${COUNT:-10}); do
  seed=$((${SEED_BASE:-1000} + i * 7919))
  line=$(BOT=$BOT OPTIONS="seed=$seed" tools/play.sh 1 "${OPPONENT:-boss}" 2>&1 | grep -E '^game' | cut -c1-60)
  if [[ -z "$line" ]]; then echo "seed=$seed: could not start, stopping"; break; fi
  echo "seed=$seed $line" >> "$OUT"
  ids+=("$(sed -E 's/game ([0-9]+):.*/\1/' <<<"$line")")
  sleep "${SPACING:-20}"
done
python3 sim/liveresult.py "${ids[@]}"
