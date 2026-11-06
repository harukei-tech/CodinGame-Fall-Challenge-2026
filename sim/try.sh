#!/usr/bin/env bash
# ボットをボスと過去版の両方に対戦させ、ロック待機の集計も出す。
# 使い方: sim/try.sh <Player.java> [試合数] [比較する過去版]
BOT=${1:-bot/Player.java}; GAMES=${2:-40}; BASE=${3:-sim/bots/v4/Player.java}
cd "$(dirname "$0")/.."
rm -rf sim/results/stderr
echo "vs boss: $(docker compose run --rm -T -e STDERR_DIR=sim/results/stderr referee bash sim/arena.sh "$GAMES" "$BOT" boss 2 2>/dev/null | tail -1)"
python3 sim/lockstats.py
echo "vs $BASE: $(docker compose run --rm -T referee bash sim/arena.sh "$GAMES" "$BOT" "$BASE" 2 2>/dev/null | tail -1)"
