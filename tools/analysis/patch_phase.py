#!/usr/bin/env python3
"""trap版に、序盤と中盤以降で戦略を切り替える仕組みを入れる。使い方: patch_phase.py <trap版> <出力>
序盤（T1〜T9）: 防衛と行き止まりの角の考慮を外し、三角形の作成だけを狙う。
中盤以降（T10〜）: 包囲の回避、塊の分散、ATTACKへの減点、行き止まりの角への減点を使う。"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 序盤の探索を使う最後のターン。これより後は、敵と接触していなくても中盤の処理へ移る。 */
    static final int OPENING_LAST_TURN = 8;
""","""    /**
     * ゲームの段階。段階ごとに使う評価を切り替える。
     * OPENING: ユニットを増やす段階。防衛と角の考慮を外し、三角形の作成だけを狙う。
     * MIDGAME: 敵と競り合う段階。包囲の回避、塊の分散、ATTACKへの減点、行き止まりの角への減点を使う。
     */
    enum GamePhase { OPENING, MIDGAME }
    /** 序盤の最後のターン。次のターンから中盤になる。 */
    static final int OPENING_PHASE_LAST_TURN = 9;
    /** 今のターンの段階。decideの最初に決める。 */
    static GamePhase phase = GamePhase.OPENING;
    /** 序盤の探索を使う最後のターン。序盤と同じにする。敵と接触したら、序盤の間でも探索をやめて通常の処理へ移る。 */
    static final int OPENING_LAST_TURN = OPENING_PHASE_LAST_TURN;
""")
rep("""        List<String> commands = new ArrayList<>();
        OpeningState openingStep = nextOpeningStep(turn);""","""        List<String> commands = new ArrayList<>();
        GamePhase newPhase = turn <= OPENING_PHASE_LAST_TURN ? GamePhase.OPENING : GamePhase.MIDGAME;
        if (newPhase != phase)
            System.err.println("P" + turn + " phase=" + newPhase);
        phase = newPhase;
        OpeningState openingStep = nextOpeningStep(turn);""")
rep("""            avoidSurrounds();
            disperseStacks();
""","""            if (isDefending()) {
                avoidSurrounds();
                disperseStacks();
            }
""")
rep("""        score -= surroundPenalty(projected);
        for (int house = 0; house < houseCount; house++) {
            score -= LOCK_WAIT_PENALTY * lockCounts[house] * projected[house];
            if (trappedHouses[house])
                score -= TRAPPED_LOCK_PENALTY * projected[house];
            if (attackable[house] && projected[house] >= STACK_RISK_UNITS)
                score -= STACK_RISK_PENALTY * projected[house];
        }""","""        boolean defending = isDefending();
        if (defending)
            score -= surroundPenalty(projected);
        for (int house = 0; house < houseCount; house++) {
            score -= LOCK_WAIT_PENALTY * lockCounts[house] * projected[house];
            if (!defending)
                continue;
            if (trappedHouses[house])
                score -= TRAPPED_LOCK_PENALTY * projected[house];
            if (attackable[house] && projected[house] >= STACK_RISK_UNITS)
                score -= STACK_RISK_PENALTY * projected[house];
        }""")
rep("""            double cost = LOCK_WAIT_PENALTY * lockCounts[c] + (trappedHouses[c] ? TRAPPED_LOCK_PENALTY : 0);""","""            double cost = LOCK_WAIT_PENALTY * lockCounts[c] + (isDefending() && trappedHouses[c] ? TRAPPED_LOCK_PENALTY : 0);""")
# 序盤の探索は序盤の段階だけで動くので、行き止まりの角への減点を外す
rep("""        int trappedUnits = 0;
        for (int house : child.units)
            if (isTrappedIn(house, child.locked))
                trappedUnits++;
        child.score = child.units.length + ownedCount - OPENING_LOCK_PENALTY * lockedCount
                - OPENING_TRAP_PENALTY * trappedUnits;""","""        child.score = child.units.length + ownedCount - OPENING_LOCK_PENALTY * lockedCount;""")
rep("""    /** 序盤の探索で、行き止まりの角にいるユニット1体への減点。 */
    static final double OPENING_TRAP_PENALTY = 0.1;
""","")
a=s.rindex("}")
s=s[:a]+"""
    /** 中盤以降ならtrueを返す。包囲の回避、塊の分散、ATTACKへの減点、行き止まりの角への減点は、中盤以降だけ使う。 */
    static boolean isDefending() {
        return phase == GamePhase.MIDGAME;
    }
}
"""
open(sys.argv[2],'w',encoding='utf8').write(s)
