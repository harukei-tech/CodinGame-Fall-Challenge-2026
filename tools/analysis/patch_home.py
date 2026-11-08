#!/usr/bin/env python3
"""phase/pb版に、中盤以降でユニット数が負けているとき自陣に戦力を集める修正を入れる。使い方: patch_home.py <元> <出力>
ランキング戦（phase、T10〜40、負けているとき）では、敵陣のユニットは占領が自陣の約1/11、包囲で倒されるのが約4倍だった。"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""","""    /** 数で負けているとき、敵陣の家にいるユニット1体への減点。 */
    static final double AWAY_UNIT_PENALTY = 0.1;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""")
rep("""    /** lockCounts[家]: その家を角に持つロック中の三角形の数。 */
""","""    /**
     * homeHouses[家]: 自陣の家なら true。影響力（各ユニットの1/(1+距離)^2の合計）が敵以上の家を自陣とする。
     * isBehindがfalseのときは、すべての家をtrueにする。
     */
    static boolean[] homeHouses;
    /** 中盤以降で、ユニット数が敵より少なければ true。このときは自陣に戦力を集める。 */
    static boolean isBehind;
    /** lockCounts[家]: その家を角に持つロック中の三角形の数。 */
""")
rep("""        lockCounts = countLocks();
""","""        lockCounts = countLocks();
        findHomeHouses();
""")
rep("""        addSurroundJobs(jobs);
        return jobs;
    }
""","""        addSurroundJobs(jobs);
        // 数で負けているときは、敵陣で占領も包囲も成り立ちにくいので、敵陣の家を含む仕事は引き受けない。
        if (isBehind)
            jobs.removeIf(job -> !allHome(job.houses));
        return jobs;
    }
""")
rep("""    static int leftoverTarget(int house) {
        int best = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {""","""    static int leftoverTarget(int house) {
        // 数で負けているときに敵陣にいるユニットは、自陣の占領できる角へ戻る。
        if (isBehind && !homeHouses[house])
            return nearestHomeCorner(house);
        int best = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {""")
rep("""            if (hasEnemy && needsMoreUnits && reachable && canFight
""","""            if (hasEnemy && needsMoreUnits && reachable && canFight && homeHouses[candidate]
""")
rep("""            if (lockCounts[candidate] == 0 && isUsefulCorner && reachable
""","""            if (lockCounts[candidate] == 0 && isUsefulCorner && reachable && homeHouses[candidate]
""")
rep("""            if (trappedHouses[house])
                score -= TRAPPED_LOCK_PENALTY * projected[house];""","""            if (trappedHouses[house])
                score -= TRAPPED_LOCK_PENALTY * projected[house];
            if (!homeHouses[house])
                score -= AWAY_UNIT_PENALTY * projected[house];""")
a=s.rindex("}")
s=s[:a]+"""
    /** 自陣の家と、数で負けているかを判定する。序盤は判定せず、すべての家を自陣とする。 */
    static void findHomeHouses() {
        homeHouses = new boolean[houseCount];
        Arrays.fill(homeHouses, true);
        int mine = 0, theirs = 0;
        for (int house = 0; house < houseCount; house++) {
            mine += myUnits[house];
            theirs += opponentUnits[house];
        }
        isBehind = isDefending() && mine < theirs;
        if (!isBehind)
            return;
        for (int house = 0; house < houseCount; house++) {
            double influence = 0;
            for (int other = 0; other < houseCount; other++) {
                int units = myUnits[other] - opponentUnits[other];
                if (units == 0 || distances[other][house] >= UNREACHABLE)
                    continue;
                double reach = 1.0 + distances[other][house];
                influence += units / (reach * reach);
            }
            homeHouses[house] = influence >= 0;
        }
    }

    /** housesがすべて自陣の家ならtrueを返す。 */
    static boolean allHome(int[] houses) {
        for (int house : houses)
            if (!homeHouses[house])
                return false;
        return true;
    }

    /** 最も近い、自陣の占領できる三角形の角を返す。なければ最も近い自陣の家、それもなければ-1。 */
    static int nearestHomeCorner(int house) {
        int corner = -1, any = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {
            if (!homeHouses[candidate] || distances[house][candidate] >= UNREACHABLE)
                continue;
            if (any < 0 || distances[house][candidate] < distances[house][any])
                any = candidate;
            if (capturableCounts[candidate] > 0
                    && (corner < 0 || distances[house][candidate] < distances[house][corner]))
                corner = candidate;
        }
        return corner >= 0 ? corner : any;
    }
}
"""
open(sys.argv[2],'w',encoding='utf8').write(s)
