#!/usr/bin/env bash
# Prints per-game surround, capture and spawn totals for the newest test game logs.
# Usage: tools/summary.sh [games]
cd "$(dirname "$0")/../logs"
sum() { grep -oE "\\\$$2 $3 [0-9]+" "$1" | awk '{s+=$3} END {print s+0}'; }
for f in $(ls -t *.log | head -"${1:-6}"); do
  echo "$f $(grep -m1 'scores:' "$f" | sed 's/ *scores: //') | lost to surround me=$(sum "$f" 1 surrounded) opp=$(sum "$f" 0 surrounded) | captured me=$(sum "$f" 0 captured) opp=$(sum "$f" 1 captured) | $(grep -m1 -o 'frame [0-9]*: .*' "$f" | cut -c1-50)"
done
