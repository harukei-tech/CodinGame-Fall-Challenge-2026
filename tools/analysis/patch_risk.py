#!/usr/bin/env python3
"""pb版に、包囲される危険を盤面全体の評価に入れる修正を入れる。使い方: patch_risk.py <元> <出力>
- moveScore: 中盤以降、包囲されうるユニット1体につきSURROUND_RISK_PENALTYを減点する。
- avoidSurrounds: 危険なユニットの数ではなく、moveScore全体が上がる手だけを採用する。
  回避で他のユニットが危険になる場合や、占領の得が減る場合は、その分も含めて判断する。"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""","""    /**
     * 包囲されうる家にいるユニット1体への減点。判定は広めで、本番では判定されたユニットの約11%が次のターンに倒された。
     * ユニット1体の価値を1.0として、その期待値より少し大きい値にする。
     */
    static final double SURROUND_RISK_PENALTY = 0.15;
    /** canBeSurroundedの作業用の配列。評価のたびに配列を作らないよう使い回す。 */
    static int[] spareEnemiesScratch, shortageScratch;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""")
rep("""        boolean defending = isDefending();
        if (defending)
            score -= surroundPenalty(projected);""","""        boolean defending = isDefending();
        if (defending) {
            score -= surroundPenalty(projected);
            score -= SURROUND_RISK_PENALTY * unitsAtRisk(projected);
        }""")
rep("""        int[] spareEnemies = opponentUnits.clone();
        int[] shortage = new int[around.length];""","""        if (spareEnemiesScratch == null || spareEnemiesScratch.length != houseCount) {
            spareEnemiesScratch = new int[houseCount];
            shortageScratch = new int[houseCount];
        }
        int[] spareEnemies = spareEnemiesScratch, shortage = shortageScratch;
        System.arraycopy(opponentUnits, 0, spareEnemies, 0, houseCount);""")
# avoidSurroundsを、moveScoreの増減で判断する形に置き換える
start=s.index("    static void avoidSurrounds() {")
end=s.index("\n    }\n",start)+7
doc=s.rindex("    /**",0,start)
s=s[:doc]+"""    /**
     * 包囲されうるユニットの近くの移動を1体ずつ変え、盤面全体の評価（moveScore）が最も上がる変更を採用する。
     * moveScoreは包囲の危険、占領、ATTACKの危険をまとめて見るので、回避で別のユニットが危険になる場合や、
     * 占領の得が減る場合は、その分も差し引いて判断される。評価が上がる変更がなくなるか、時間が来たら終える。
     */
    static void avoidSurrounds() {
        // 止まっている敵は包囲してこないので、警戒すると自分の動きを止めるだけになる。
        if (enemyIdleTurns >= ENEMY_IDLE_TURNS)
            return;
        int[] projected = projectedUnits();
        if (unitsAtRisk(projected) == 0)
            return;
        double score = moveScore(projected);
        while (System.nanoTime() - turnStartNanos < SURROUND_CHECK_BUDGET_NANOS) {
            int bestFrom = -1, bestOldTo = -1, bestNewTo = -1;
            double bestScore = score + 1e-9;
            boolean[] nearRisk = housesNearRisk(projected);
            for (int from = 0; from < houseCount; from++) {
                if (myUnits[from] == 0 || !nearRisk[from])
                    continue;
                int staying = stayingUnits(from);
                for (int oldTo = 0; oldTo < houseCount; oldTo++) {
                    boolean hasUnitGoing = oldTo == from ? staying > 0 : moves[from][oldTo] > 0;
                    if (!hasUnitGoing)
                        continue;
                    projected[oldTo]--;
                    for (int newTo = -1; newTo < neighbors[from].length; newTo++) {
                        // newToが-1の案は「移動をやめて、その場に残る」を表す。
                        int destination = newTo < 0 ? from : neighbors[from][newTo];
                        if (destination == oldTo)
                            continue;
                        projected[destination]++;
                        double newScore = moveScore(projected);
                        projected[destination]--;
                        if (newScore > bestScore) {
                            bestScore = newScore;
                            bestFrom = from;
                            bestOldTo = oldTo;
                            bestNewTo = destination;
                        }
                    }
                    projected[oldTo]++;
                }
            }
            if (bestFrom < 0)
                return;
            if (bestOldTo != bestFrom)
                moves[bestFrom][bestOldTo]--;
            if (bestNewTo != bestFrom)
                moves[bestFrom][bestNewTo]++;
            projected[bestOldTo]--;
            projected[bestNewTo]++;
            score = bestScore;
        }
    }
"""+s[end:]
open(sys.argv[2],'w',encoding='utf8').write(s)
