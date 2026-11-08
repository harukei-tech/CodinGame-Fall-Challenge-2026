#!/usr/bin/env bash
# f134が負け越した相手4体と同じシードで本番テストを行い、logs/live-results/<名前>_<相手>.txt に保存する。
# 相手は TheCodeWitcher, 0x6E0FF, comzine, Palmipedus（2026-11-08時点のID）。OPPS で変えられる。
# 以前の 300〜350位の4体（6860726 6856420 6857105 6849550）は、f134が16勝4敗で差を測れなかった。
# 使い方: tools/bench.sh <名前> <Player.java>   （COUNT でシード数、既定は5）
cd "$(dirname "$0")/.."
NAME=$1; BOT=$2
for opp in ${OPPS:-6859249 6861640 6849503 6849501}; do
  COUNT=${COUNT:-5} SEED_BASE=3000 OPPONENT=$opp tools/seeds.sh "$BOT" "logs/live-results/${NAME}_$opp.txt" | tail -1
  sleep 20
done
