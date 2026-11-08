#!/usr/bin/env python3
"""f134系のボットに「ロック中の三角形から1歩で出られない角」への減点を入れる。使い方: patch_trap.py <元> <出力>"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""","""    /**
     * ロック中の三角形の角で、隣の家がすべてその三角形の角である家（行き止まりの角）にいるユニット1体への減点。
     * そこから1歩動いても三角形の角に残るので、解除に2ターン以上かかる。隣が2軒だけの角などが当たる。
     * ロック数の少なさだけで比べると、こうした角が選ばれやすいので、それを打ち消す大きさにする。
     */
    static final double TRAPPED_LOCK_PENALTY = 0.2;
    /** 序盤の探索で、行き止まりの角にいるユニット1体への減点。 */
    static final double OPENING_TRAP_PENALTY = 0.1;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""")
rep("""    /** lockCounts[家]: その家を角に持つロック中の三角形の数。 */
""","""    /** trappedHouses[家]: ロック中の三角形の行き止まりの角なら true。TRAPPED_LOCK_PENALTYを参照。 */
    static boolean[] trappedHouses;
    /** lockCounts[家]: その家を角に持つロック中の三角形の数。 */
""")
rep("""        lockCounts = countLocks();
        findThreats();
""","""        lockCounts = countLocks();
        trappedHouses = findTrappedHouses();
        findThreats();
""")
rep("""            score -= LOCK_WAIT_PENALTY * lockCounts[house] * projected[house];
""","""            score -= LOCK_WAIT_PENALTY * lockCounts[house] * projected[house];
            if (trappedHouses[house])
                score -= TRAPPED_LOCK_PENALTY * projected[house];
""")
# 中盤のスポーン先
old_start=s.index("    static int spawnCorner(int t) {")
old_end=s.index("\n    }\n",old_start)+7
doc_start=s.rindex("    /**",0,old_start)
s=s[:doc_start]+"""    /**
     * 生まれる角の初期値を選ぶ。ロックを止める減点と、行き止まりの角への減点の合計が最も小さい角にする。
     * 同点なら、占領できる三角形に近い角を選ぶ。局所探索がこの後で変更する。
     */
    static int spawnCorner(int t) {
        int best = triHouses[t][0], bestDist = Integer.MAX_VALUE;
        double bestCost = Double.MAX_VALUE;
        for (int c : triHouses[t]) {
            int d = UNREACHABLE;
            for (int u = 0; u < triCount; u++) {
                if (owner[u] == 0 || !meCanCapture[u])
                    continue;
                for (int k : triHouses[u])
                    d = Math.min(d, distances[c][k]);
            }
            double cost = LOCK_WAIT_PENALTY * lockCounts[c] + (trappedHouses[c] ? TRAPPED_LOCK_PENALTY : 0);
            if (cost < bestCost - 1e-9 || Math.abs(cost - bestCost) <= 1e-9 && d < bestDist) {
                bestCost = cost;
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
"""+s[old_end:]
# 序盤の探索
rep("""        child.score = child.units.length + ownedCount - OPENING_LOCK_PENALTY * lockedCount;""","""        int trappedUnits = 0;
        for (int house : child.units)
            if (isTrappedIn(house, child.locked))
                trappedUnits++;
        child.score = child.units.length + ownedCount - OPENING_LOCK_PENALTY * lockedCount
                - OPENING_TRAP_PENALTY * trappedUnits;""")
a=s.rindex("}")
s=s[:a]+"""
    /** 今の盤面で、ロック中の三角形の行き止まりの角を返す。SPAWNする三角形をロック中にした後で呼ぶ。 */
    static boolean[] findTrappedHouses() {
        boolean[] locked = new boolean[triCount];
        for (int triangle = 0; triangle < triCount; triangle++)
            locked[triangle] = isLocked(triangle);
        boolean[] trapped = new boolean[houseCount];
        for (int house = 0; house < houseCount; house++)
            trapped[house] = isTrappedIn(house, locked);
        return trapped;
    }

    /** houseが、lockedのいずれかの三角形の角で、隣の家がすべてその三角形の角ならtrueを返す。 */
    static boolean isTrappedIn(int house, boolean[] locked) {
        if (neighbors[house].length == 0)
            return false;
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (!locked[triangle] || !isCorner(triangle, house))
                continue;
            boolean allInside = true;
            for (int neighbor : neighbors[house])
                allInside &= isCorner(triangle, neighbor);
            if (allInside)
                return true;
        }
        return false;
    }
}
"""
open(sys.argv[2],'w',encoding='utf8').write(s)
