#!/usr/bin/env bash
# Runs test games on CodinGame and prints one result line per game.
# Usage: tools/play.sh [games] [opponent]   (opponent: default, boss, me or an agent_id)
# Full logs go to logs/<test_game_id>.log. Stays under the 10 test games per minute limit.
set -euo pipefail
GAMES=${1:-5}
OPPONENT=${2:-default}
API=https://www.codingame.com/api/v1
C=$API/challenges/fall-challenge-2026
cd "$(dirname "$0")/.."
mkdir -p logs
cg() { curl -s -H "Authorization: Bearer $(cat ~/.config/codingame/token)" "$@"; }

wins=0
for ((i = 1; i <= GAMES; i++)); do
  if [[ "$OPPONENT" =~ ^[0-9]+$ ]]; then opp="[$OPPONENT]"; else opp="[\"$OPPONENT\"]"; fi
  resp=$(jq -n --rawfile code bot/Player.java --argjson opp "$opp" \
      '{language: "Java", code: $code, opponents: $opp}' \
    | cg -X POST "$C/test-games?wait=30" -H 'Content-Type: application/json' --data-binary @-)
  id=$(jq -r '.test_game_id // empty' <<<"$resp")
  if [[ -z "$id" ]]; then echo "failed to start: $resp"; exit 1; fi
  while [[ "$(jq -r .status <<<"$resp")" != done && "$(jq -r .status <<<"$resp")" != failed ]]; do
    resp=$(cg -H 'Accept: application/json' "$API/test-games/$id?wait=30")
  done
  jq -r '.log // .hint // .' <<<"$resp" > "logs/$id.log"
  scores=$(grep -m1 'scores:' "logs/$id.log" || echo 'no scores')
  ranking=$(grep -m1 'ranking' "logs/$id.log" || true)
  errors=$(grep -c -E 'error:|not provided|Task can.t be applied|Unknown command' "logs/$id.log" || true)
  [[ "$ranking" == *": 0 1"* ]] && wins=$((wins + 1))
  echo "game $id:$scores | errors=$errors | test_games left: $(jq -r '.limits.test_games.remaining // "?"' <<<"$resp")"
  ((i < GAMES)) && sleep 7
done
echo "wins: $wins/$GAMES"
