#!/usr/bin/env python3
"""f134系のボットに「ロックの家の余りユニットは隣の家から1歩を選ぶ」修正を入れる。使い方: patch_unlockstep.py <元> <出力>"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""","""    /**
     * ロックの家にいる余りユニットに、遠くの目的地ではなく隣の家から1歩を選ばせる最後のターン。
     * 後半はロックだらけになり、隣の家の比較で選べなくなるので、元の方式に戻す。
     */
    static final int UNLOCK_STEP_LAST_TURN = 100;
    /** ロック中の三角形がこの割合以上になったら、1歩を選ぶ方式をやめて元の方式に戻す。 */
    static final double UNLOCK_STEP_MAX_LOCKED_SHARE = 0.5;
    /** 1歩を選ぶとき、占領できる三角形の角へ向かう手への小さな加点。価値が同じなら先につながる家を選ぶ。 */
    static final double UNLOCK_STEP_CORNER_BONUS = 0.05;
    /** leftoverTargetの戻り値。行き先の代わりに、隣の家から1歩を選ぶことを表す。 */
    static final int STEP_OFF_LOCK = -2;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""")
rep("""    static int houseCount;
""","""    static int houseCount;
    /** 今のターン番号。decideの最初に記録する。 */
    static int currentTurn;
""")
rep("""        List<String> commands = new ArrayList<>();
        OpeningState openingStep = nextOpeningStep(turn);""","""        List<String> commands = new ArrayList<>();
        currentTurn = turn;
        OpeningState openingStep = nextOpeningStep(turn);""")
rep("""                int target = leftoverTarget(house);
""","""                int target = leftoverTarget(house);
                if (target == STEP_OFF_LOCK) {
                    int step = chooseUnlockStep(house);
                    if (step != house) {
                        moves[house][step]++;
                        committed[step]++;
                    } else {
                        // 動いても得がないので残し、局所探索に任せる。
                        committed[house]++;
                        idleLeftovers[house]++;
                    }
                    continue;
                }
""")
rep("""        // 近くに敵がいないので、再占領できるようにロック中の家から離れ、占領できる三角形の角へ向かう。
""","""        // 序盤から中盤は、遠くの家を目指すと途中で別のロックの角を通るので、隣の家から1歩を選ぶ。
        if (!enemyIsClose && lockCounts[house] > 0 && isUnlockStepActive())
            return STEP_OFF_LOCK;

        // 近くに敵がいないので、再占領できるようにロック中の家から離れ、占領できる三角形の角へ向かう。
""") if s.count("// 近くに敵がいないので、再占領できるようにロック中の家から離れ、占領できる三角形の角へ向かう。")==1 else rep("""        // 近くに敵がいないので、三角形を再占領できるようにロック中の家から離れる。
""","""        // 序盤から中盤は、遠くの家を目指すと途中で別のロックの角を通るので、隣の家から1歩を選ぶ。
        if (!enemyIsClose && lockCounts[house] > 0 && isUnlockStepActive())
            return STEP_OFF_LOCK;

        // 近くに敵がいないので、三角形を再占領できるようにロック中の家から離れる。
""")
a=s.rindex("}")
s=s[:a]+"""
    /** 隣の家から1歩を選ぶ方式を使うならtrueを返す。ロックが増える後半は使わない。 */
    static boolean isUnlockStepActive() {
        int locked = 0;
        for (int triangle = 0; triangle < triCount; triangle++)
            if (isLocked(triangle))
                locked++;
        return currentTurn <= UNLOCK_STEP_LAST_TURN && locked < UNLOCK_STEP_MAX_LOCKED_SHARE * triCount;
    }

    /**
     * ロックの家にいる余りユニット1体の行き先を、その家と隣の家の中から選ぶ。
     * 各案の価値は、今の計画の配置と比べた次の3つの増減の合計にする。
     * - 占領できる三角形の価値。隣を押さえて家を持つ占領も含む。
     * - 包囲で倒せる敵ユニットの数。
     * - ロック中の三角形の空いた角の数。あと1つで解除できる三角形ほど点が大きい。
     * ロックを止める移動でも、占領や包囲の得がそれを上回れば選ばれる。
     * 同じ価値なら、ユニットの少ない家を選び、同じ家のユニットが1か所に固まらないようにする。
     */
    static int chooseUnlockStep(int house) {
        int[] projected = projectedUnits();
        double base = unlockStepValue(projected);
        int best = house;
        double bestGain = 0;
        projected[house]--;
        for (int neighbor : neighbors[house]) {
            if (enemyDeepHouses[neighbor])
                continue;
            projected[neighbor]++;
            double gain = unlockStepValue(projected) - base;
            if (capturableCounts[neighbor] > 0)
                gain += UNLOCK_STEP_CORNER_BONUS;
            boolean better = gain > bestGain + 1e-9
                    || Math.abs(gain - bestGain) <= 1e-9 && best != house && projected[neighbor] < projected[best];
            projected[neighbor]--;
            if (better) {
                bestGain = gain;
                best = neighbor;
            }
        }
        return best;
    }

    /** chooseUnlockStepで比べる、配置の価値。 */
    static double unlockStepValue(int[] projected) {
        double value = capturedValue(projected) + VALUE_SURROUND_PER_UNIT * enemyUnitsSurrounded(projected);
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (!isLocked(triangle))
                continue;
            int empty = 0;
            for (int corner : triHouses[triangle])
                if (projected[corner] == 0)
                    empty++;
            value += VALUE_UNLOCK * UNLOCK_CREDIT[empty];
        }
        return value;
    }

    /** projectedの配置で、包囲されて全滅する敵ユニットの数を返す。審判と同じく、隣の家すべてで自軍が多い家の敵が対象。 */
    static int enemyUnitsSurrounded(int[] projected) {
        int killed = 0;
        for (int house = 0; house < houseCount; house++) {
            if (opponentUnits[house] == 0 || neighbors[house].length == 0)
                continue;
            boolean surrounded = true;
            for (int neighbor : neighbors[house])
                surrounded &= projected[neighbor] > opponentUnits[neighbor];
            if (surrounded)
                killed += opponentUnits[house];
        }
        return killed;
    }
}
"""
open(sys.argv[2],'w',encoding='utf8').write(s)
