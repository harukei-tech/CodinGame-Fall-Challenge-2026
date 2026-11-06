#!/usr/bin/env bash
# referee コンテナの中で実行する。ボットをビルドして対戦を並列に行う。
# 使い方: sim/arena.sh <試合数> <ボットのPlayer.java> [相手のPlayer.java | boss] [並列数] [リーグ番号]
set -euo pipefail
cd /workspace
GAMES=${1:-20}; BOT=${2:-bot/Player.java}; OPPONENT=${3:-boss}; JOBS=${4:-4}; LEAGUE=${5:-2}
REF=sim/ref
BUILD=/tmp/sim-build
mkdir -p "$BUILD/me" "$BUILD/opp" "$BUILD/tools" sim/results
if [[ ! -f $REF/target/classpath.txt ]]; then
  (cd $REF && mvn -q -B test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Dmdep.includeScope=test)
fi
cp sim/harness/Arena.java $REF/src/test/java/Arena.java
(cd $REF && mvn -q -B test-compile)
javac -nowarn -d "$BUILD/me" "$BOT"
javac -nowarn -d "$BUILD/tools" sim/harness/BossClient.java
ME="java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp $BUILD/me Player"
if [[ $OPPONENT == boss ]]; then
  OPP="java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp $BUILD/tools BossClient boss 9000"
else
  javac -nowarn -d "$BUILD/opp" "$OPPONENT"
  OPP="java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -cp $BUILD/opp Player"
fi
CP="$REF/target/test-classes:$REF/target/classes:$(cat $REF/target/classpath.txt)"
export ME OPP CP LEAGUE
play() {
  seed=$1
  # 奇数シードは席を入れ替えて、席による差をなくす。
  if (( seed % 2 == 0 )); then a=$ME; b=$OPP; seat=0; else a=$OPP; b=$ME; seat=1; fi
  line=$(java -XX:+UseSerialGC -cp "$CP" Arena "$seed" "$LEAGUE" "$a" "$b" 2>/dev/null | grep '^RESULT' || echo "RESULT seed=$seed FAILED")
  echo "seat=$seat $line"
}
export -f play
seq 1 "$GAMES" | xargs -P "$JOBS" -I{} bash -c 'play {}' | tee sim/results/last.txt > /dev/null
# 勝敗を席に関係なく「自分」から見た形で集計する。
awk '{
  seat = substr($1, 6); split($4, a, "="); split($5, b, "="); s0 = a[2] + 0; s1 = b[2] + 0
  if ($3 ~ /FAILED/ || $4 == "FAILED") { failed++; next }
  me = seat == 0 ? s0 : s1; opp = seat == 0 ? s1 : s0
  if (me > opp) win++; else if (me < opp) loss++; else draw++
  if ($0 ~ /timeout/) timeouts++
  n++; myTotal += me; oppTotal += opp
} END {
  printf "games=%d win=%d loss=%d draw=%d failed=%d timeouts=%d avg_score=%.1f/%.1f\n", n, win, loss, draw, failed, timeouts, n ? myTotal / n : 0, n ? oppTotal / n : 0
}' sim/results/last.txt
