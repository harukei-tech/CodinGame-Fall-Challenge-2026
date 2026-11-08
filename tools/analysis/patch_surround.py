#!/usr/bin/env python3
"""f134系のボットに、包囲で敵を倒す価値を占領の探索と移動の評価へ入れる修正を入れる。使い方: patch_surround.py <元> <出力>"""
import sys
s=open(sys.argv[1],encoding='utf8').read()
def rep(a,b):
    global s
    assert s.count(a)==1,(a,s.count(a)); s=s.replace(a,b)
rep("""    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""","""    /**
     * 移動後の配置で包囲できる敵ユニット1体の価値。敵が次のターンも同じ家に残る確率が約55%なので、
     * 1体を倒す価値（中立の三角形1つと同じ1.0）に0.55を掛けた値にする。ランキング戦のT9〜T40の実測。
     */
    static final double SURROUND_KILL_VALUE = 0.55;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
""")
rep("""    /** 今の敵の配置で占領できる三角形の価値の合計を返す。 */
    static double capturedValue(int[] projected) {
        boolean[] owned = housesOwned(projected);
        double value = 0;""","""    /** 今の敵の配置で占領できる三角形の価値と、包囲で倒せる敵の価値の合計を返す。 */
    static double capturedValue(int[] projected) {
        boolean[] owned = housesOwned(projected);
        double value = SURROUND_KILL_VALUE * killableEnemies(projected);""")
rep("""    static double captureScore(int[] projected) {
        boolean[] owned = housesOwned(projected);
        double score = 0;""","""    static double captureScore(int[] projected) {
        boolean[] owned = housesOwned(projected);
        double score = SURROUND_KILL_VALUE * killableEnemies(projected);""")
rep("""        score -= surroundPenalty(projected);
""","""        score -= surroundPenalty(projected);
        score += SURROUND_KILL_VALUE * killableEnemies(projected);
""")
a=s.rindex("}")
s=s[:a]+"""
    /** projectedの配置で、敵が動かなければ包囲で全滅する敵ユニットの数を返す。隣の家すべてで自軍が多い家の敵が対象。 */
    static int killableEnemies(int[] projected) {
        int killed = 0;
        for (int house = 0; house < houseCount; house++) {
            if (opponentUnits[house] == 0 || neighbors[house].length == 0)
                continue;
            boolean surrounded = true;
            for (int neighbor : neighbors[house]) {
                if (projected[neighbor] <= opponentUnits[neighbor]) {
                    surrounded = false;
                    break;
                }
            }
            if (surrounded)
                killed += opponentUnits[house];
        }
        return killed;
    }
}
"""
open(sys.argv[2],'w',encoding='utf8').write(s)
