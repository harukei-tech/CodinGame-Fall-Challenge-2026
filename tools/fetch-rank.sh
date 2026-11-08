#!/usr/bin/env bash
# サブミットのランキング戦のログを logs/rank-<ID>/ に保存する。使い方: tools/fetch-rank.sh <submission_id>
# トークンは ~/.config/codingame/token から読む。画面には出さない。
set -e
cd "$(dirname "$0")/.."
ID=$1; D=logs/rank-$ID; mkdir -p "$D/logs"
API=https://www.codingame.com/api/v1
H="Authorization: Bearer $(cat ~/.config/codingame/token)"
B=""; : > "$D/battles.jsonl"
while :; do
  R=$(curl -s -H "$H" "$API/challenges/fall-challenge-2026/battles?submission=$ID&limit=100${B:+&before=$B}")
  jq -c '.battles[]' <<<"$R" >> "$D/battles.jsonl"
  B=$(jq -r '.next_before // empty' <<<"$R"); [ -z "$B" ] && break
done
# 1分120リクエストの制限に余裕を持たせる。応答はJSONではなくテキスト。
for G in $(jq -r .game_id "$D/battles.jsonl"); do
  [ -s "$D/logs/$G.log" ] || { curl -s -H "$H" "$API/games/$G" > "$D/logs/$G.log"; sleep 0.8; }
done
echo "saved $(ls "$D/logs" | wc -l) games to $D"
