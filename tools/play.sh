#!/usr/bin/env bash
# Runs test games on CodinGame and prints one result line per game.
# Usage: tools/play.sh [games] [opponent]   (opponent: default, boss, me or an agent_id)
# Set BOT=<path> to send another Player.java, and OPTIONS=seed=<n> to fix the map.
# Full logs go to logs/<test_game_id>.log. Stays under the 10 test games per minute limit.
set -euo pipefail
GAMES=${1:-5}
OPPONENT=${2:-default}
BOT=${BOT:-bot/Player.java}
OPTIONS=${OPTIONS:-}
API=https://www.codingame.com/api/v1
C=$API/challenges/fall-challenge-2026
cd "$(dirname "$0")/.."
mkdir -p logs
cg() { curl -s -H "Authorization: Bearer $(cat ~/.config/codingame/token)" "$@"; }

wins=0
for ((i = 1; i <= GAMES; i++)); do
  if [[ "$OPPONENT" =~ ^[0-9]+$ ]]; then opp="[$OPPONENT]"; else opp="[\"$OPPONENT\"]"; fi
  resp=$(jq -n --rawfile code "$BOT" --argjson opp "$opp" --arg options "$OPTIONS" \
      '{language: "Java", code: $code, opponents: $opp} + (if $options == "" then {} else {game_options: $options} end)' \
    | cg -X POST "$C/test-games?wait=30" -H 'Content-Type: application/json' --data-binary @-)
  id=$(jq -r '.test_game_id // empty' <<<"$resp")
  if [[ -z "$id" ]]; then echo "failed to start: $resp"; exit 1; fi
  # 待ち時間内に終わらなかった試合は、テキスト形式の応答で終わるまで待つ。
  # 応答の形式が想定と違っても連続でリクエストしないよう、毎回間隔を空けて回数も制限する。
  status=$(jq -r '.status // empty' <<<"$resp" 2>/dev/null || true)
  if [[ "$status" == done || "$status" == failed ]]; then
    jq -r '.log // .hint // .' <<<"$resp" > "logs/$id.log"
  else
    for ((poll = 1; poll <= 20; poll++)); do
      text=$(cg "$API/test-games/$id?wait=30")
      if grep -q -m1 -E '^status: (done|failed)' <<<"$text" || grep -q -m1 'scores:' <<<"$text"; then
        status=done
        break
      fi
      sleep 5
    done
    if [[ "$status" != done ]]; then echo "game $id: no result, stopping"; exit 1; fi
    printf '%s\n' "$text" > "logs/$id.log"
  fi
  scores=$(grep -m1 'scores:' "logs/$id.log" || echo 'no scores')
  ranking=$(grep -m1 'ranking' "logs/$id.log" || true)
  errors=$(grep -c -E 'error:|not provided|Task can.t be applied|Unknown command' "logs/$id.log" || true)
  [[ "$ranking" == *": 0 1"* ]] && wins=$((wins + 1))
  echo "game $id:$scores | errors=$errors | test_games left: $(jq -r '.limits.test_games.remaining // "?"' <<<"$resp" 2>/dev/null || echo "?")"
  ((i < GAMES)) && sleep 7
done
echo "wins: $wins/$GAMES"
