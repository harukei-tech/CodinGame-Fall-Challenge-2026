import java.io.*;
import java.util.*;

/**
 * Fall Challenge 2026 (TryAngle Catch) bot. Uses MOVE, SPAWN and ATTACK.
 *
 * Rules that drive the design (from the referee source):
 * - A captured triangle stays ours after our units leave, so units keep moving
 * to new triangles.
 * - There is no combat on a house: enemies only die by being surrounded. Tying
 * a corner is enough
 * to stop the opponent from owning it.
 * - SPAWN makes the triangle neutral, and we can only recapture it once our
 * units have left all
 * three corners.
 * - 家の持ち主は、隣の家すべてで多い側になる。そこにユニットがいなくてもよい。
 * 角Xにユニットを送る代わりに、Xの隣をすべて押さえても占領できる。隣が2軒だけなら残り2つの角で足りる。
 * - 包囲の判定は隣の家だけを見る。隣の家をすべて敵に押さえられると、
 * 自分の家に何体いても全滅する。移動の後に判定するので、敵は1ターンで囲める。
 */
class Player {
    static final int MAX_TURNS = 200;
    /**
     * Stop spawning this many turns before the end: a held triangle then outscores
     * one more unit.
     */
    static final int SPAWN_STOP_TURNS_LEFT = 40;
    /**
     * Once we lead by this many units, stop spawning and keep the triangles for
     * points.
     */
    static final int SPAWN_UNIT_LEAD = 8;
    /** Spawn regardless of the lead before this turn. */
    static final int SPAWN_FREE_UNTIL_TURN = 60;
    static final double VALUE_NEUTRAL = 1.0;
    static final double VALUE_ENEMY = 4.0;
    static final double VALUE_DEFEND = 2.0;
    /** 敵を包囲する仕事の価値。倒せる敵ユニット1体あたりの値。 */
    static final double VALUE_SURROUND_PER_UNIT = 1.0;
    /** 局所探索での、包囲される恐れへの減点。ユニット1体あたりの値。 */
    static final double SURROUND_DANGER_PENALTY = 0;
    /** 角の隣を押さえて占領する案で、押さえる追加の家の数の上限。 */
    static final int COVER_MAX_EXTRA_HOUSES = 2;
    /** 角の隣を押さえて占領する案の得点の倍率。その角が空くのでロック解除が速く、そこの敵も倒せる。 */
    static final double COVER_BONUS = 1.0;
    /** ADD_PATHを使う条件。三角形を1つ手放すので、新しい三角形をこの数以上すぐ占領できるときだけ使う。 */
    static final int ADD_PATH_MIN_TRIANGLES = 2;
    /** REMOVE_PATHを使う条件。三角形を1つ手放すので、敵の三角形をこの数以上消せるときだけ使う。 */
    static final int REMOVE_PATH_MIN_TRIANGLES = 2;
    /** 隣の家がこの数より多い敵は、囲むのに手間がかかるので狙わない。 */
    static final int SURROUND_MAX_NEIGHBORS = 4;
    /** 敵は動くので、この移動ターン数以内に囲める場合だけ狙う。 */
    static final int SURROUND_MAX_TIME = 2;
    /** 余りユニットは、この距離以内に敵がいれば、ロック解除より敵を優先する。 */
    static final int LEFTOVER_ENEMY_CLOSE_DISTANCE = 2;
    /** 倒せる敵がこの数以上のときだけATTACKする。自軍も3体失うので、それより多く倒す必要がある。 */
    static final int ATTACK_MIN_KILLS = 4;
    /** この数以上で固まった家は、敵のATTACKに狙われるものとして減点する。 */
    static final int STACK_RISK_UNITS = 3;
    /** ATTACKされうる家でこの数以上の塊は、隣の家へ分けて損失を抑える。 */
    static final int DISPERSE_STACK_UNITS = 4;
    /** 次のターンにATTACKされうる塊への、1体あたりの減点。1体ずつ逃がす手が採用されるよう、計画変更の減点より大きくする。 */
    static final double STACK_RISK_PENALTY = 0.4;
    /** ロック中の三角形を解除できる配置の価値。 */
    static final double VALUE_UNLOCK = 0.5;
    /** ロック中の三角形の、空いた角の数ごとの得点の割合。1体離れるごとに点が増えるので、探索が離れる手を選べる。 */
    static final double[] UNLOCK_CREDIT = { 0, 0.2, 0.5, 1 };
    /** ロック中の角で待つユニット1体、止めている三角形1つあたりの減点。多くの三角形を止める家ほど減点が大きい。 */
    static final double LOCK_WAIT_PENALTY = 0.05;
    /** 三角形の3つの角のうち、条件を満たす角の数ごとの得点の割合。途中の段階にも点を付けて探索を導く。 */
    static final double[] PARTIAL_CREDIT = { 0, 0.05, 0.25, 1 };
    /** 局所探索で、ユニット1体の行き先を元の計画から変えるときの減点。 */
    static final double PLAN_CHANGE_PENALTY = 0.05;
    /** 移動計画の局所探索に使える時間。 */
    static final long LOCAL_SEARCH_BUDGET_NANOS = 15_000_000L;
    /** 敵の配置がこのターン数変わらなければ、敵は止まっていると見なす。 */
    static final int ENEMY_IDLE_TURNS = 2;
    /** 包囲対策に使える時間。1ターン50msの制限に余裕を残す。 */
    static final long SURROUND_CHECK_BUDGET_NANOS = 32_000_000L;

    static int houseCount;
    static int[] houseXs, houseYs, myUnits, opponentUnits;
    static int myScore, opponentScore;
    /** 道がないなどで、たどり着けない家までの距離を表す値。どの実際の距離よりも大きい。 */
    static final int UNREACHABLE = 1000;

    /** isConnected[家A][家B]: 家Aと家Bが道で直接つながっていれば true。 */
    static boolean[][] isConnected;
    /** neighbors[家]: その家と直接つながっている家の一覧。 */
    static int[][] neighbors;
    /** distances[家A][家B]: 家Aから家Bまでの最短の移動ターン数。 */
    static int[][] distances;
    /** linkHouses[i]: 新しく道を引ける家の組。Goldから送られてくる。 */
    static int[][] linkHouses;
    static int linkCount;
    static int triCount;
    static int[][] triHouses;
    static int[] owner;
    static boolean[] meCanCapture, opCanCapture;

    /** free[家]: その家にいるユニットのうち、今ターンの役割がまだ決まっていない数。 */
    static int[] free;
    /** committed[家]: その家に残る、またはその家へ向かうと決めたユニットの数。到着が先のターンでも数える。 */
    static int[] committed;
    /** moves[移動元][移動先]: 今ターンに隣の家へ動かすユニットの数。最後にMOVEコマンドへ変換する。 */
    static int[][] moves;
    /** 今ターンにSPAWNする三角形の一覧と、それぞれの生まれる角。移動と一緒に局所探索で決める。 */
    static List<Integer> spawnTriangles;
    static int[] spawnCorners;
    /** spawnedUnits[家]: 今ターンのSPAWNでその家に生まれるユニットの数。 */
    static int[] spawnedUnits;
    /** idleLeftovers[家]: 仕事も行き先もなく、その家に残る予定のユニットの数。局所探索で自由に動かせる。 */
    static int[] idleLeftovers;
    /** lockCounts[家]: その家を角に持つロック中の三角形の数。 */
    static int[] lockCounts;
    /** 包囲の分析用。前のターンの移動後の予定配置と、そのとき包囲の恐れがあると見た家。 */
    static int[] lastProjected;
    static boolean[] lastAtRisk;
    static int lastRiskTotal;
    static boolean lastAvoidSkipped;
    /** 敵が次のターンの始めにATTACKに使える可能性のある三角形。 */
    static int[] threatTriangles;
    /** enemyReachable[家]: 敵が今ターンの移動後にその家に集められる最大の数。 */
    static int[] enemyReachable;
    /** attackTargets[三角形]: その三角形からATTACKできる家の一覧。 */
    static int[][] attackTargets;
    /** moveScoreの作業用。その家がATTACKされうるならtrue。 */
    static boolean[] attackable;

    static final Random random = new Random(0);
    /** 前のターンの敵ユニットの配置。 */
    static int[] previousOpponentUnits;
    /** 敵の配置が続けて変わっていないターン数。 */
    static int enemyIdleTurns;

    /** 今ターンの入力を読み終えた時刻。 */
    static long turnStartNanos;

    static StreamTokenizer in;

    static int next() throws IOException {
        if (in.nextToken() == StreamTokenizer.TT_EOF)
            System.exit(0);
        return (int) in.nval;
    }

    public static void main(String[] args) throws IOException {
        in = new StreamTokenizer(new BufferedReader(new InputStreamReader(System.in)));
        houseCount = next();
        houseXs = new int[houseCount];
        houseYs = new int[houseCount];
        for (int i = 0; i < houseCount; i++) {
            int houseId = next();
            houseXs[houseId] = next();
            houseYs[houseId] = next();
        }
        myUnits = new int[houseCount];
        opponentUnits = new int[houseCount];
        for (int turn = 1;; turn++) {
            readTurn();
            turnStartNanos = System.nanoTime();
            updateEnemyIdle();
            logTurn(turn);
            logSurroundLosses(turn);
            System.out.println(decide(turn));
            System.out.flush();
            // 出力の後に書くので、ログの計算はターンの制限時間を使わない。
            logLocks(turn);
        }
    }

    /** テスト対戦のログで戦況を追うため、ターンごとの要約を標準エラーに出す。 */
    static void logTurn(int turn) {
        int mine = 0, theirs = 0, myTriangles = 0, theirTriangles = 0;
        for (int house = 0; house < houseCount; house++) {
            mine += myUnits[house];
            theirs += opponentUnits[house];
        }
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (owner[triangle] == 0)
                myTriangles++;
            else if (owner[triangle] == 1)
                theirTriangles++;
        }
        System.err.println("T" + turn + " score=" + myScore + "/" + opponentScore + " units=" + mine + "/" + theirs
                + " tri=" + myTriangles + "/" + theirTriangles + "/" + triCount);
    }

    /** ロック中の三角形の角で待機しているユニットの数を、標準エラーに出す。 */
    static void logLocks(int turn) {
        int[] locks = lockCounts;
        int locked = 0, waiting = 0, hubWaiting = 0, blockedByWaiting = 0;
        for (int triangle = 0; triangle < triCount; triangle++)
            if (isLocked(triangle))
                locked++;
        for (int house = 0; house < houseCount; house++) {
            int staying = stayingUnits(house);
            if (staying == 0 || locks[house] == 0)
                continue;
            waiting += staying;
            blockedByWaiting += locks[house];
            if (locks[house] >= 2)
                hubWaiting += staying;
        }
        int[] projected = projectedUnits();
        lastProjected = projected;
        lastAtRisk = new boolean[houseCount];
        lastRiskTotal = 0;
        for (int house = 0; house < houseCount; house++) {
            lastAtRisk[house] = projected[house] > 0 && canBeSurrounded(house, projected);
            if (lastAtRisk[house])
                lastRiskTotal += projected[house];
        }
        lastAvoidSkipped = enemyIdleTurns >= ENEMY_IDLE_TURNS;
        markAttackable(projected);
        int exposed = 0;
        for (int house = 0; house < houseCount; house++)
            if (attackable[house] && projected[house] >= STACK_RISK_UNITS)
                exposed += projected[house];
        System.err.println("L" + turn + " locked=" + locked + " waiting=" + waiting + " hubWaiting=" + hubWaiting
                + " blocked=" + blockedByWaiting + " exposed=" + exposed);
    }

    /** 自分が再占領できない三角形なら true。SPAWNやATTACKの後、角から全員が離れるまで続く。 */
    static boolean isLocked(int triangle) {
        return owner[triangle] != 0 && !meCanCapture[triangle];
    }

    /** 家ごとに、その家を角に持つロック中の三角形の数を返す。 */
    static int[] countLocks() {
        int[] locks = new int[houseCount];
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (isLocked(triangle))
                for (int corner : triHouses[triangle])
                    locks[corner]++;
        }
        return locks;
    }

    /**
     * 前のターンの予定と今の配置を比べ、包囲で失ったユニットを標準エラーに出す。
     * 予測できていたか（predicted）、逃げる余地があったか（risk）で、対策の場所を判断するために使う。
     */
    static void logSurroundLosses(int turn) {
        if (lastProjected == null)
            return;
        int lost = 0, predicted = 0;
        for (int house = 0; house < houseCount; house++) {
            if (lastProjected[house] == 0 || myUnits[house] > 0 || neighbors[house].length == 0)
                continue;
            boolean surrounded = true;
            for (int neighbor : neighbors[house])
                surrounded &= opponentUnits[neighbor] > myUnits[neighbor];
            if (!surrounded)
                continue;
            lost += lastProjected[house];
            if (lastAtRisk[house])
                predicted += lastProjected[house];
        }
        if (lost > 0)
            System.err.println("S" + turn + " lost=" + lost + " predicted=" + predicted + " riskBefore=" + lastRiskTotal
                    + " skipped=" + (lastAvoidSkipped ? 1 : 0));
    }

    /** 敵が動いたかどうかを記録する。 */
    static void updateEnemyIdle() {
        enemyIdleTurns = Arrays.equals(opponentUnits, previousOpponentUnits) ? enemyIdleTurns + 1 : 0;
        previousOpponentUnits = opponentUnits.clone();
    }

    static void readTurn() throws IOException {
        myScore = next();
        opponentScore = next();
        for (int i = 0; i < houseCount; i++) {
            int houseId = next();
            myUnits[houseId] = next();
            opponentUnits[houseId] = next();
        }
        isConnected = new boolean[houseCount][houseCount];
        int pathCount = next();
        for (int i = 0; i < pathCount; i++) {
            int house1 = next(), house2 = next();
            isConnected[house1][house2] = isConnected[house2][house1] = true;
        }
        triCount = next();
        triHouses = new int[triCount][3];
        owner = new int[triCount];
        meCanCapture = new boolean[triCount];
        opCanCapture = new boolean[triCount];
        for (int i = 0; i < triCount; i++) {
            for (int k = 0; k < 3; k++)
                triHouses[i][k] = next();
            owner[i] = next();
            meCanCapture[i] = next() == 1;
            opCanCapture[i] = next() == 1;
        }
        linkCount = next();
        linkHouses = new int[linkCount][2];
        for (int i = 0; i < linkCount; i++) {
            linkHouses[i][0] = next();
            linkHouses[i][1] = next();
        }
        buildGraph();
    }

    /**
     * 今ターンの道の情報（isConnected）から、経路計算に使う2つの表を作る。
     * - neighbors[家]: その家と道で直接つながっている家の一覧
     * - distances[家A][家B]: 家Aから家Bまでの最短の移動ターン数（道を1本進むと1ターン）
     */
    static void buildGraph() {
        // 手順1: 家ごとに、隣の家の一覧を作る。
        neighbors = new int[houseCount][];
        for (int house = 0; house < houseCount; house++) {
            // 配列のサイズを決めるため、先に隣の家の数を数える。
            int neighborCount = 0;
            for (int other = 0; other < houseCount; other++)
                if (isConnected[house][other])
                    neighborCount++;

            // 隣の家の番号を配列に詰める。
            neighbors[house] = new int[neighborCount];
            int nextIndex = 0;
            for (int other = 0; other < houseCount; other++)
                if (isConnected[house][other])
                    neighbors[house][nextIndex++] = other;
        }

        // 手順2: すべての家を出発点として幅優先探索（BFS）を行い、最短距離を求める。
        // 道の長さはすべて1ターンなので、近い家から順に調べれば最短距離が求まる。
        distances = new int[houseCount][houseCount];
        // 各家は1回の探索で1度しか入らないので、サイズはhouseCountで足りる。
        int[] queue = new int[houseCount];
        for (int start = 0; start < houseCount; start++) {
            int[] distanceFromStart = distances[start];
            // 最初は全部「到達不能」にしておき、たどり着けた家だけ距離を書き換える。
            Arrays.fill(distanceFromStart, UNREACHABLE);
            distanceFromStart[start] = 0;

            // queue[head]からqueue[tail - 1]までが、これから調べる家の待ち行列。
            int head = 0, tail = 0;
            queue[tail++] = start;
            while (head < tail) {
                int current = queue[head++];
                for (int neighbor : neighbors[current]) {
                    // より短い距離で着けるなら記録する（未訪問の家が該当する）。
                    if (distanceFromStart[neighbor] > distanceFromStart[current] + 1) {
                        distanceFromStart[neighbor] = distanceFromStart[current] + 1;
                        queue[tail++] = neighbor;
                    }
                }
            }
        }
    }

    static String decide(int turn) {
        List<String> commands = new ArrayList<>();
        addAttacks(commands);
        addPathAction(commands, turn);
        // SPAWNは移動の後で占領の前に処理される。先に決めれば、同じターンに角を離れられ、
        // 生まれるユニットをそのターンの占領にも使える。
        chooseSpawns(turn);

        free = myUnits.clone();
        committed = new int[houseCount];
        moves = new int[houseCount][houseCount];
        idleLeftovers = new int[houseCount];
        lockCounts = countLocks();
        findThreats();
        placeSpawns();

        assignTargets();
        assignLeftovers();
        improveMoves();
        avoidSurrounds();
        disperseStacks();

        for (int from = 0; from < houseCount; from++) {
            for (int to = 0; to < houseCount; to++) {
                if (moves[from][to] > 0)
                    commands.add("MOVE " + from + " " + to + " " + moves[from][to]);
            }
        }
        addSpawns(commands);
        return commands.isEmpty() ? "WAIT" : String.join(";", commands);
    }

    /**
     * 3つの角にユニットがいる自分の三角形で、隣の敵の塊を倒す。
     * ATTACKは移動より先に処理されるので、盤面の情報を攻撃後の状態に更新してから移動を決める。
     */
    static void addAttacks(List<String> commands) {
        for (int triangle = 0; triangle < triCount; triangle++) {
            int[] corners = triHouses[triangle];
            if (owner[triangle] != 0 || myUnits[corners[0]] == 0 || myUnits[corners[1]] == 0
                    || myUnits[corners[2]] == 0)
                continue;
            int target = -1;
            for (int corner : corners) {
                for (int neighbor : neighbors[corner]) {
                    if (target < 0 || opponentUnits[neighbor] > opponentUnits[target])
                        target = neighbor;
                }
            }
            if (target < 0 || opponentUnits[target] < ATTACK_MIN_KILLS)
                continue;
            commands.add("ATTACK " + corners[0] + " " + corners[1] + " " + corners[2] + " " + target);
            opponentUnits[target] = 0;
            owner[triangle] = -1;
            meCanCapture[triangle] = false;
            for (int corner : corners)
                myUnits[corner]--;
        }
    }

    /**
     * 自分の三角形を1つ使い、ADD_PATHかREMOVE_PATHを1回だけ行う。得が大きい方を選ぶ。
     * 道の処理は移動の後なので、消費する1体を各角で確保してから移動を決める。
     */
    static void addPathAction(List<String> commands, int turn) {
        int bestTriangle = -1, bestGain = 0, addFrom = -1, addTo = -1;
        String bestCommand = null;
        for (int triangle = 0; triangle < triCount; triangle++) {
            int[] corners = triHouses[triangle];
            if (owner[triangle] != 0 || myUnits[corners[0]] == 0 || myUnits[corners[1]] == 0
                    || myUnits[corners[2]] == 0)
                continue;
            // 道を引くと各角の1体を失うので、その後の配置で評価する。
            for (int corner : corners)
                myUnits[corner]--;
            for (int i = 0; i < linkCount; i++) {
                for (int side = 0; side < 2; side++) {
                    int from = linkHouses[i][side], to = linkHouses[i][1 - side];
                    if (!isCorner(triangle, from))
                        continue;
                    int gain = capturableNewTriangles(from, to);
                    if (gain >= ADD_PATH_MIN_TRIANGLES && gain > bestGain) {
                        bestGain = gain;
                        bestTriangle = triangle;
                        bestCommand = "ADD_PATH " + cornerOrder(triangle, from) + " " + to;
                        addFrom = from;
                        addTo = to;
                    }
                }
            }
            for (int from : corners) {
                for (int to : neighbors[from]) {
                    if (isCorner(triangle, to))
                        continue;
                    int gain = enemyTrianglesOnPath(from, to);
                    if (gain >= REMOVE_PATH_MIN_TRIANGLES && gain > bestGain) {
                        bestGain = gain;
                        bestTriangle = triangle;
                        bestCommand = "REMOVE_PATH " + cornerOrder(triangle, from) + " " + to;
                        addFrom = -1;
                    }
                }
            }
            for (int corner : corners)
                myUnits[corner]++;
        }
        if (bestTriangle < 0)
            return;
        commands.add(bestCommand);
        System.err.println("P" + turn + " " + bestCommand + " gain=" + bestGain);
        for (int corner : triHouses[bestTriangle])
            myUnits[corner]--;
        owner[bestTriangle] = -1;
        meCanCapture[bestTriangle] = false;
        if (addFrom >= 0)
            holdNewTriangles(addFrom, addTo);
    }

    /**
     * 新しい三角形をこのターンに占領できるよう、その角のユニットを動かさない。
     * 移動の計画から隠すために、盤面の情報のユニット数から引いておく。
     */
    static void holdNewTriangles(int a, int b) {
        boolean[] held = new boolean[houseCount];
        held[a] = held[b] = true;
        for (int third : neighbors[a])
            if (isConnected[b][third] && myUnits[third] > opponentUnits[third] && isEmptyTriangle(a, b, third))
                held[third] = true;
        for (int house = 0; house < houseCount; house++)
            if (held[house])
                myUnits[house] -= opponentUnits[house] + 1;
    }

    static boolean isCorner(int triangle, int house) {
        int[] corners = triHouses[triangle];
        return corners[0] == house || corners[1] == house || corners[2] == house;
    }

    /** 三角形の角を、firstが先頭になる順に並べた文字列を返す。コマンドの先頭の角が道の端になる。 */
    static String cornerOrder(int triangle, int first) {
        StringBuilder order = new StringBuilder().append(first);
        for (int corner : triHouses[triangle])
            if (corner != first)
                order.append(' ').append(corner);
        return order.toString();
    }

    /** 家aと家bを結ぶと生まれる三角形のうち、今の配置のまま自分のものになる数を返す。 */
    static int capturableNewTriangles(int a, int b) {
        if (myUnits[a] <= opponentUnits[a] || myUnits[b] <= opponentUnits[b])
            return 0;
        int count = 0;
        for (int third : neighbors[a]) {
            if (isConnected[b][third] && myUnits[third] > opponentUnits[third] && isEmptyTriangle(a, b, third))
                count++;
        }
        return count;
    }

    /** 家aと家bの道を含む三角形のうち、敵の数から自分の数を引いた値を返す。 */
    static int enemyTrianglesOnPath(int a, int b) {
        int count = 0;
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (!isCorner(triangle, a) || !isCorner(triangle, b))
                continue;
            if (owner[triangle] == 1)
                count++;
            else if (owner[triangle] == 0)
                count--;
        }
        return count;
    }

    /** 3つの家の内側に他の家がなければtrueを返す。内側に家があると三角形として認められない。 */
    static boolean isEmptyTriangle(int a, int b, int c) {
        for (int house = 0; house < houseCount; house++) {
            if (house == a || house == b || house == c)
                continue;
            boolean side1 = isCounterClockwise(a, b, house), side2 = isCounterClockwise(b, c, house),
                    side3 = isCounterClockwise(c, a, house);
            if (side1 == side2 && side2 == side3)
                return false;
        }
        return true;
    }

    /** 審判のccwと同じ判定。 */
    static boolean isCounterClockwise(int a, int b, int c) {
        return (long) (houseYs[c] - houseYs[a]) * (houseXs[b] - houseXs[a])
                > (long) (houseYs[b] - houseYs[a]) * (houseXs[c] - houseXs[a]);
    }

    /**
     * One job: bring `required[i]` units to `houses[i]`, for a triangle to capture
     * or defend.
     */
    static final class Job {
        final int[] houses;
        final int[] required;
        final double value;
        final int triangle;
        /** この移動ターン数を超える仕事は引き受けない。 */
        final int maxTime;

        Job(int triangle, int[] houses, int[] required, double value) {
            this(triangle, houses, required, value, Integer.MAX_VALUE);
        }

        Job(int triangle, int[] houses, int[] required, double value, int maxTime) {
            this.triangle = triangle;
            this.houses = houses;
            this.required = required;
            this.value = value;
            this.maxTime = maxTime;
        }
    }

    static List<Job> buildJobs() {
        List<Job> jobs = new ArrayList<>();
        for (int t = 0; t < triCount; t++) {
            if (owner[t] != 0 && meCanCapture[t]) {
                int[] required = new int[3];
                for (int k = 0; k < 3; k++) {
                    int corner = triHouses[t][k];
                    // 隣が2軒だけの角は、残りの角を押さえれば手に入るので、ユニットを送らない。
                    required[k] = isFreeCorner(corner) ? 0 : opponentUnits[corner] + 1;
                }
                double value = owner[t] == 1 ? VALUE_ENEMY : VALUE_NEUTRAL;
                jobs.add(new Job(t, triHouses[t], required, value));
                addCoverJobs(jobs, t, value);
            } else if (owner[t] == 0 && threatened(t)) {
                int corner = cheapestBlock(t);
                jobs.add(new Job(t, new int[] { corner }, new int[] { enemyReach(corner) }, VALUE_DEFEND));
            }
        }
        addSurroundJobs(jobs);
        return jobs;
    }

    /**
     * 三角形の角Xごとに、Xへ送らずXの隣をすべて押さえて占領する案を追加する。
     * 残りの2つの角はどの案でも必要なので、押さえる家は「残りの角」と「Xの残りの隣」になる。
     * 別の仕事で隣を押さえる予定があれば、scoreJobでその分のコストが0になり、この案が選ばれやすくなる。
     */
    static void addCoverJobs(List<Job> jobs, int triangle, double value) {
        int[] corners = triHouses[triangle];
        for (int covered : corners) {
            int extraCount = neighbors[covered].length - 2;
            if (extraCount < 1 || extraCount > COVER_MAX_EXTRA_HOUSES)
                continue;
            int[] houses = new int[2 + extraCount];
            int count = 0;
            for (int corner : corners)
                if (corner != covered)
                    houses[count++] = corner;
            for (int neighbor : neighbors[covered])
                if (neighbor != houses[0] && neighbor != houses[1])
                    houses[count++] = neighbor;
            int[] required = new int[houses.length];
            for (int i = 0; i < houses.length; i++)
                required[i] = isFreeCorner(houses[i]) && i < 2 ? 0 : opponentUnits[houses[i]] + 1;
            jobs.add(new Job(triangle, houses, required, value * COVER_BONUS));
        }
    }

    /**
     * 三角形の角で隣が2軒だけなら、その2軒は三角形の残りの角なので、そこを押さえれば持ち主になれる。
     */
    static boolean isFreeCorner(int corner) {
        return neighbors[corner].length == 2;
    }

    /** 敵のいる家ごとに、隣の家をすべて押さえて敵を倒す仕事を追加する。 */
    static void addSurroundJobs(List<Job> jobs) {
        for (int enemyHouse = 0; enemyHouse < houseCount; enemyHouse++) {
            int[] around = neighbors[enemyHouse];
            if (opponentUnits[enemyHouse] == 0 || around.length == 0 || around.length > SURROUND_MAX_NEIGHBORS)
                continue;
            int[] required = new int[around.length];
            for (int i = 0; i < around.length; i++)
                required[i] = opponentUnits[around[i]] + 1;
            double value = VALUE_SURROUND_PER_UNIT * opponentUnits[enemyHouse];
            jobs.add(new Job(-1, around, required, value, SURROUND_MAX_TIME));
        }
    }

    /** Most enemy units that can stand on this house next turn. */
    static int enemyReach(int house) {
        int total = opponentUnits[house];
        for (int v : neighbors[house])
            total += opponentUnits[v];
        return total;
    }

    /**
     * True if the opponent could own all three corners of our triangle next turn.
     */
    static boolean threatened(int t) {
        for (int c : triHouses[t]) {
            if (enemyReach(c) <= myUnits[c])
                return false;
        }
        return true;
    }

    static int cheapestBlock(int t) {
        int best = triHouses[t][0];
        for (int c : triHouses[t])
            if (enemyReach(c) < enemyReach(best))
                best = c;
        return best;
    }

    /**
     * Greedily gives free units to the job with the best value for its time and
     * travel.
     */
    static void assignTargets() {
        List<Job> jobs = buildJobs();
        while (!jobs.isEmpty()) {
            Job best = null;
            double bestScore = -1;
            for (Job job : jobs) {
                double score = scoreJob(job);
                if (score > bestScore) {
                    bestScore = score;
                    best = job;
                }
            }
            if (best == null)
                return;
            commit(best);
            jobs.remove(best);
            // 同じ三角形の別の案は、もう要らない。
            final int done = best.triangle;
            if (done >= 0)
                jobs.removeIf(job -> job.triangle == done);
        }
    }

    /**
     * Returns the job's score, or -1 if we do not have enough free units for it.
     */
    static double scoreJob(Job job) {
        int[] pool = free.clone();
        int time = 0, travel = 0;
        for (int i = 0; i < job.houses.length; i++) {
            int house = job.houses[i];
            int want = job.required[i] - committed[house];
            for (; want > 0; want--) {
                int from = nearestUnit(pool, house);
                if (from < 0)
                    return -1;
                pool[from]--;
                time = Math.max(time, distances[from][house]);
                travel += distances[from][house];
            }
        }
        if (time > job.maxTime)
            return -1;
        return job.value / (1 + time + 0.25 * travel);
    }

    static void commit(Job job) {
        for (int i = 0; i < job.houses.length; i++) {
            int house = job.houses[i];
            while (committed[house] < job.required[i]) {
                int from = nearestUnit(free, house);
                if (from < 0)
                    return;
                free[from]--;
                committed[house]++;
                if (from != house)
                    moves[from][stepToward(from, house)]++;
            }
        }
    }

    static int nearestUnit(int[] pool, int house) {
        int best = -1;
        for (int h = 0; h < houseCount; h++) {
            if (pool[h] > 0 && distances[h][house] < UNREACHABLE
                    && (best < 0 || distances[h][house] < distances[best][house]))
                best = h;
        }
        return best;
    }

    /**
     * fromからtargetへの最短経路のうち、次に進む隣の家を返す。
     * 最短経路が複数あるときは、敵ユニットが最も少ない家を選ぶ。
     */
    static int stepToward(int from, int target) {
        int best = -1;
        for (int neighbor : neighbors[from]) {
            // targetに1歩近づく隣の家だけが、最短経路の候補になる。
            boolean isCloser = distances[neighbor][target] == distances[from][target] - 1;
            if (isCloser && (best < 0 || opponentUnits[neighbor] < opponentUnits[best]))
                best = neighbor;
        }
        return best;
    }

    /**
     * assignTargetsで仕事をもらえなかった「余りユニット」の行き先を、1体ずつ決める。
     * 行き先の選び方はleftoverTargetを参照。行き先がなければその場に残す。
     */
    static void assignLeftovers() {
        for (int house = 0; house < houseCount; house++) {
            while (free[house] > 0) {
                free[house]--;
                int target = leftoverTarget(house);
                if (target >= 0 && target != house) {
                    // 今ターンは隣の家へ1歩だけ進み、次のターンにまた行き先を決め直す。
                    moves[house][stepToward(house, target)]++;
                    committed[target]++;
                } else {
                    // 行き先がない、または今いる家が行き先なら、その場に残る。
                    committed[house]++;
                    idleLeftovers[house]++;
                }
            }
        }
    }

    /**
     * houseにいる余りユニットの行き先を返す。行き先がなければ-1を返す。
     * 優先順位:
     * 1. 最も近い「敵がいて、自軍の割り当てがまだ敵の数を超えていない家」
     * 2. 1がなく、今いる家がロック中なら、最も近い「ロック中でない家」
     *
     * ロック中の三角形は、審判が毎ターン送る全三角形の一覧からlockCountsに集計している。
     */
    static int leftoverTarget(int house) {
        int best = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {
            boolean hasEnemy = opponentUnits[candidate] > 0;
            // 割り当てが敵より多くなれば家を確保できるので、それ以上は送らない。
            // まだ行き先が決まっていない、その家のユニットも味方として数える。
            boolean needsMoreUnits = committed[candidate] + free[candidate] <= opponentUnits[candidate];
            boolean reachable = distances[house][candidate] < UNREACHABLE;
            if (hasEnemy && needsMoreUnits && reachable
                    && (best < 0 || distances[house][candidate] < distances[house][best]))
                best = candidate;
        }
        boolean enemyIsClose = best >= 0 && distances[house][best] <= LEFTOVER_ENEMY_CLOSE_DISTANCE;
        if (enemyIsClose || lockCounts[house] == 0)
            return best;

        // 近くに敵がいないので、三角形を再占領できるようにロック中の家から離れる。
        int enemyTarget = best;
        best = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {
            boolean reachable = distances[house][candidate] < UNREACHABLE;
            if (lockCounts[candidate] == 0 && reachable
                    && (best < 0 || distances[house][candidate] < distances[house][best]))
                best = candidate;
        }
        return best >= 0 ? best : enemyTarget;
    }

    /**
     * 移動計画をランダムに少しずつ変え、moveScoreが上がる変更だけを採用する。
     * 上がらない変更は捨てるので、遠くの仕事へ向かう移動はそのまま残る。
     */
    static void improveMoves() {
        int realUnits = 0;
        for (int house = 0; house < houseCount; house++)
            realUnits += myUnits[house];
        // SPAWNで生まれるユニットも、三角形の角を選ぶユニットとして一緒に探索する。
        int unitCount = realUnits + spawnCorners.length;
        if (unitCount == 0)
            return;

        // ユニット1体ごとの移動元と移動先の一覧に展開する。
        int[] unitFrom = new int[unitCount], unitTo = new int[unitCount];
        // 待っているだけのユニットには守る計画がないので、行き先を変えても減点しない。
        boolean[] isIdle = new boolean[unitCount];
        int index = 0;
        for (int from = 0; from < houseCount; from++) {
            int staying = stayingUnits(from);
            for (int i = 0; i < staying; i++) {
                isIdle[index] = i < idleLeftovers[from];
                unitFrom[index] = from;
                unitTo[index++] = from;
            }
            for (int to = 0; to < houseCount; to++) {
                for (int i = 0; i < moves[from][to]; i++) {
                    unitFrom[index] = from;
                    unitTo[index++] = to;
                }
            }
        }
        for (int i = 0; i < spawnCorners.length; i++) {
            isIdle[index] = true;
            unitFrom[index] = -1;
            unitTo[index++] = spawnCorners[i];
        }

        int[] projected = projectedUnits();
        // 元の計画は遠くの仕事へ向かう移動を含むので、確かな得がなければ変えない。
        int[] plannedTo = unitTo.clone();
        int deviations = 0;
        double bestScore = moveScore(projected);
        long deadline = System.nanoTime() + LOCAL_SEARCH_BUDGET_NANOS;
        for (int iteration = 0;; iteration++) {
            if ((iteration & 255) == 0 && System.nanoTime() > deadline)
                break;
            int unit = random.nextInt(unitCount);
            int newTo;
            if (unit >= realUnits) {
                int[] corners = triHouses[spawnTriangles.get(unit - realUnits)];
                newTo = corners[random.nextInt(3)];
            } else {
                int[] options = neighbors[unitFrom[unit]];
                int choice = random.nextInt(options.length + 1);
                newTo = choice == options.length ? unitFrom[unit] : options[choice];
            }
            int oldTo = unitTo[unit];
            if (newTo == oldTo)
                continue;
            projected[oldTo]--;
            projected[newTo]++;
            int newDeviations = deviations;
            if (!isIdle[unit])
                newDeviations += (newTo != plannedTo[unit] ? 1 : 0) - (oldTo != plannedTo[unit] ? 1 : 0);
            double score = moveScore(projected) - PLAN_CHANGE_PENALTY * newDeviations;
            if (score > bestScore + 1e-9) {
                bestScore = score;
                deviations = newDeviations;
                unitTo[unit] = newTo;
            } else {
                projected[oldTo]++;
                projected[newTo]--;
            }
        }

        moves = new int[houseCount][houseCount];
        for (int unit = 0; unit < realUnits; unit++) {
            if (unitTo[unit] != unitFrom[unit])
                moves[unitFrom[unit]][unitTo[unit]]++;
        }
        spawnedUnits = new int[houseCount];
        for (int i = 0; i < spawnCorners.length; i++) {
            spawnCorners[i] = unitTo[realUnits + i];
            spawnedUnits[spawnCorners[i]]++;
        }
    }

    /**
     * 敵が次のターンの始めにATTACKに使えそうな三角形を集める。
     * ATTACKは移動より先に処理されるので、今ターンの移動後の配置が、次のターンの攻撃を受ける。
     * 敵が持っている三角形に加え、敵が今ターンに占領できそうな三角形も含める。
     */
    static void findThreats() {
        enemyReachable = new int[houseCount];
        for (int house = 0; house < houseCount; house++)
            enemyReachable[house] = enemyReach(house);
        attackTargets = new int[triCount][];
        attackable = new boolean[houseCount];
        int[] found = new int[triCount];
        int count = 0;
        for (int triangle = 0; triangle < triCount; triangle++) {
            if ((owner[triangle] == 1 || opCanCapture[triangle]) && enemyCanFill(triangle)) {
                found[count++] = triangle;
                attackTargets[triangle] = housesNextTo(triHouses[triangle]);
            }
        }
        threatTriangles = Arrays.copyOf(found, count);
    }

    /** 敵が今ターンの移動で、三角形の3つの角すべてにユニットを置けそうならtrueを返す。 */
    static boolean enemyCanFill(int triangle) {
        boolean[] counted = new boolean[houseCount];
        int enemies = 0;
        for (int corner : triHouses[triangle]) {
            if (enemyReachable[corner] == 0)
                return false;
            if (!counted[corner]) {
                counted[corner] = true;
                enemies += opponentUnits[corner];
            }
            for (int neighbor : neighbors[corner]) {
                if (!counted[neighbor]) {
                    counted[neighbor] = true;
                    enemies += opponentUnits[neighbor];
                }
            }
        }
        return enemies >= 3;
    }

    /** cornersのいずれかの隣にある家の一覧を返す。 */
    static int[] housesNextTo(int[] corners) {
        boolean[] isNear = new boolean[houseCount];
        int count = 0;
        for (int corner : corners) {
            for (int neighbor : neighbors[corner]) {
                if (!isNear[neighbor]) {
                    isNear[neighbor] = true;
                    count++;
                }
            }
        }
        int[] houses = new int[count];
        int index = 0;
        for (int house = 0; house < houseCount; house++)
            if (isNear[house])
                houses[index++] = house;
        return houses;
    }

    /** projectedの配置で敵がATTACKできる家を、attackableに記録する。 */
    static void markAttackable(int[] projected) {
        Arrays.fill(attackable, false);
        for (int triangle : threatTriangles) {
            if (owner[triangle] != 1 && isCaptureBlocked(triangle, projected))
                continue;
            for (int target : attackTargets[triangle])
                attackable[target] = true;
        }
    }

    /** 隣が2軒だけの角を除き、角のどれかで自軍が敵と同数以上なら、敵はその三角形を占領できない。 */
    static boolean isCaptureBlocked(int triangle, int[] projected) {
        for (int corner : triHouses[triangle])
            if (!isFreeCorner(corner) && projected[corner] >= enemyReachable[corner])
                return true;
        return false;
    }

    /**
     * projectedの配置で、審判の判定と同じように自分の家になる家をtrueにして返す。
     * 隣の家すべてで多い側が持ち主になり、どちらでもなければ、その家で多い側が持ち主になる。
     */
    static boolean[] housesOwned(int[] projected) {
        boolean[] owned = new boolean[houseCount];
        for (int house = 0; house < houseCount; house++) {
            boolean allMine = neighbors[house].length > 0, allEnemy = neighbors[house].length > 0;
            for (int neighbor : neighbors[house]) {
                allMine &= projected[neighbor] > opponentUnits[neighbor];
                allEnemy &= opponentUnits[neighbor] > projected[neighbor];
            }
            owned[house] = allMine || !allEnemy && projected[house] > opponentUnits[house];
        }
        return owned;
    }

    /** 移動後の配置の良さを返す。次のターンの占領とロック解除を加点し、狙われる塊を減点する。 */
    static double moveScore(int[] projected) {
        double score = 0;
        markAttackable(projected);
        boolean[] owned = housesOwned(projected);
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (owner[triangle] == 0)
                continue;
            int[] corners = triHouses[triangle];
            int a = corners[0], b = corners[1], c = corners[2];
            if (meCanCapture[triangle]) {
                int held = (owned[a] ? 1 : 0) + (owned[b] ? 1 : 0) + (owned[c] ? 1 : 0);
                double value = owner[triangle] == 1 ? VALUE_ENEMY : VALUE_NEUTRAL;
                score += value * PARTIAL_CREDIT[held];
            } else {
                int empty = (projected[a] == 0 ? 1 : 0) + (projected[b] == 0 ? 1 : 0) + (projected[c] == 0 ? 1 : 0);
                score += VALUE_UNLOCK * UNLOCK_CREDIT[empty];
            }
        }
        score -= surroundPenalty(projected);
        for (int house = 0; house < houseCount; house++) {
            score -= LOCK_WAIT_PENALTY * lockCounts[house] * projected[house];
            if (attackable[house] && projected[house] >= STACK_RISK_UNITS)
                score -= STACK_RISK_PENALTY * projected[house];
        }
        return score;
    }

    /**
     * 包囲される恐れへの減点を返す。敵に押さえられそうな隣の家の割合の2乗に比例する。
     * 味方と隣り合う配置へ導き、逃げ道がなくなる前に離れさせる。
     * 敵を囲む配置への加点は入れない。ボスはほぼ毎ターン動くので、本番で逆効果だった。
     */
    static double surroundPenalty(int[] projected) {
        double penalty = 0;
        for (int house = 0; house < houseCount; house++) {
            int[] around = neighbors[house];
            if (around.length == 0)
                continue;
            if (projected[house] > 0) {
                int lost = 0;
                for (int neighbor : around)
                    if (enemyReachable[neighbor] > projected[neighbor])
                        lost++;
                double ratio = (double) lost / around.length;
                penalty += SURROUND_DANGER_PENALTY * projected[house] * ratio * ratio;
            }
        }
        return penalty;
    }

    /**
     * 立てた移動計画を見直し、次のターンに包囲されるユニットが減るように修正する。
     * ユニット1体の行き先を変える案をすべて試し、最も危険が減る案を採用する。
     * 危険が減らなくなるか、時間がなくなったら終わる。
     */
    static void avoidSurrounds() {
        // 止まっている敵は包囲してこないので、警戒すると自分の動きを止めるだけになる。
        if (enemyIdleTurns >= ENEMY_IDLE_TURNS)
            return;
        int[] projected = projectedUnits();
        int risk = unitsAtRisk(projected);
        while (risk > 0 && System.nanoTime() - turnStartNanos < SURROUND_CHECK_BUDGET_NANOS) {
            int bestFrom = -1, bestOldTo = -1, bestNewTo = -1, bestRisk = risk;
            boolean[] nearRisk = housesNearRisk(projected);
            markAttackable(projected);
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
                        // 包囲から逃げた先で、ATTACKに撃たれる塊を作らない。
                        if (attackable[destination] && projected[destination] + 1 >= STACK_RISK_UNITS)
                            continue;
                        projected[destination]++;
                        int newRisk = unitsAtRisk(projected);
                        projected[destination]--;
                        if (newRisk < bestRisk) {
                            bestRisk = newRisk;
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
            risk = bestRisk;
        }
    }

    /**
     * ATTACKされうる家の大きな塊から、1体ずつ隣の家へ分ける。
     * ATTACKは1軒の全員を倒すので、避けられないときは塊を小さくして損失を抑える。
     * 占領の計画を壊さないよう、対象は大きな塊だけに絞る。
     */
    static void disperseStacks() {
        if (enemyIdleTurns >= ENEMY_IDLE_TURNS)
            return;
        int[] projected = projectedUnits();
        while (System.nanoTime() - turnStartNanos < SURROUND_CHECK_BUDGET_NANOS) {
            markAttackable(projected);
            int stack = -1;
            for (int house = 0; house < houseCount; house++) {
                if (attackable[house] && projected[house] >= DISPERSE_STACK_UNITS
                        && (stack < 0 || projected[house] > projected[stack]))
                    stack = house;
            }
            if (stack < 0 || !moveOneUnitAway(stack, projected))
                return;
        }
    }

    /**
     * stackへ向かう、またはstackに残るユニットを1体、より小さな家へ変更する。
     * 行き先は、撃たれない家を優先し、その中で最もユニットが少ない家を選ぶ。変更できなければfalseを返す。
     */
    static boolean moveOneUnitAway(int stack, int[] projected) {
        int bestFrom = -1, bestTo = -1, bestCost = Integer.MAX_VALUE;
        for (int from = 0; from < houseCount; from++) {
            boolean hasUnit = from == stack ? stayingUnits(from) > 0 : moves[from][stack] > 0;
            if (!hasUnit)
                continue;
            for (int to = -1; to < neighbors[from].length; to++) {
                int destination = to < 0 ? from : neighbors[from][to];
                if (destination == stack || projected[destination] + 1 >= projected[stack] - 1)
                    continue;
                int cost = projected[destination] + (attackable[destination] ? houseCount : 0);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestFrom = from;
                    bestTo = destination;
                }
            }
        }
        if (bestFrom < 0)
            return false;
        if (bestFrom != stack)
            moves[bestFrom][stack]--;
        if (bestTo != bestFrom)
            moves[bestFrom][bestTo]++;
        projected[stack]--;
        projected[bestTo]++;
        return true;
    }

    /** 今ターンの移動とSPAWNの後に、各家にいる自分のユニット数を返す。 */
    static int[] projectedUnits() {
        int[] projected = myUnits.clone();
        for (int house = 0; house < houseCount; house++)
            projected[house] += spawnedUnits[house];
        for (int from = 0; from < houseCount; from++) {
            for (int to = 0; to < houseCount; to++) {
                projected[from] -= moves[from][to];
                projected[to] += moves[from][to];
            }
        }
        return projected;
    }

    /** fromにいるユニットのうち、今ターンに動かないユニットの数を返す。 */
    static int stayingUnits(int from) {
        int staying = myUnits[from];
        for (int to = 0; to < houseCount; to++)
            staying -= moves[from][to];
        return staying;
    }

    /** 包囲される恐れのある家と、その隣の家をtrueにした配列を返す。修正案を試す範囲を絞るために使う。 */
    static boolean[] housesNearRisk(int[] projected) {
        boolean[] near = new boolean[houseCount];
        for (int house = 0; house < houseCount; house++) {
            if (projected[house] > 0 && canBeSurrounded(house, projected)) {
                near[house] = true;
                for (int neighbor : neighbors[house])
                    near[neighbor] = true;
            }
        }
        return near;
    }

    /** 次のターンに包囲される恐れのあるユニットの合計を返す。 */
    static int unitsAtRisk(int[] projected) {
        int total = 0;
        for (int house = 0; house < houseCount; house++) {
            if (projected[house] > 0 && canBeSurrounded(house, projected))
                total += projected[house];
        }
        return total;
    }

    /**
     * 敵が次のターンに、houseの隣の家すべてで自分より多くなれるかを判定する。
     * 敵ユニットは1体につき1か所にしか行けないので、余っている敵だけを足りない家へ回して数える。
     */
    static boolean canBeSurrounded(int house, int[] projected) {
        int[] around = neighbors[house];
        if (around.length == 0)
            return false;
        int[] spareEnemies = opponentUnits.clone();
        int[] shortage = new int[around.length];
        for (int i = 0; i < around.length; i++) {
            int neighbor = around[i];
            int enemiesNeeded = projected[neighbor] + 1;
            int kept = Math.min(spareEnemies[neighbor], enemiesNeeded);
            spareEnemies[neighbor] -= kept;
            shortage[i] = enemiesNeeded - kept;
            // 敵はSPAWNでも1体増やせる。SPAWNは移動の後、包囲の判定の前に行われる。
            if (shortage[i] > 0 && enemyCanSpawnOn(neighbor))
                shortage[i]--;
        }
        for (int i = 0; i < around.length; i++) {
            for (int source : neighbors[around[i]]) {
                int sent = Math.min(spareEnemies[source], shortage[i]);
                spareEnemies[source] -= sent;
                shortage[i] -= sent;
            }
            if (shortage[i] > 0)
                return false;
        }
        return true;
    }

    /** 敵が持っている三角形の角なら、敵はそこにSPAWNできる。 */
    static boolean enemyCanSpawnOn(int house) {
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (owner[triangle] != 1)
                continue;
            for (int corner : triHouses[triangle])
                if (corner == house)
                    return true;
        }
        return false;
    }

    /**
     * 今ターンにSPAWNする三角形を決め、ロック中として扱うように盤面の情報を更新する。
     * 移動を決める前に呼ぶ。
     */
    static void chooseSpawns(int turn) {
        spawnTriangles = new ArrayList<>();
        // 敵が止まったら、三角形を持ち続けるだけで得点差が開いていく。
        if (MAX_TURNS - turn < SPAWN_STOP_TURNS_LEFT || enemyIdleTurns >= ENEMY_IDLE_TURNS)
            return;
        int mine = 0, theirs = 0;
        for (int i = 0; i < houseCount; i++) {
            mine += myUnits[i];
            theirs += opponentUnits[i];
        }
        for (int t = 0; t < triCount; t++) {
            if (owner[t] != 0)
                continue;
            if (turn >= SPAWN_FREE_UNTIL_TURN && mine >= theirs + SPAWN_UNIT_LEAD)
                break;
            spawnTriangles.add(t);
            mine++;
        }
        for (int t : spawnTriangles) {
            owner[t] = -1;
            meCanCapture[t] = false;
        }
    }

    /** 生まれる角の初期値を決める。局所探索がこの後で変更する。 */
    static void placeSpawns() {
        spawnCorners = new int[spawnTriangles.size()];
        spawnedUnits = new int[houseCount];
        for (int i = 0; i < spawnCorners.length; i++) {
            spawnCorners[i] = spawnCorner(spawnTriangles.get(i));
            spawnedUnits[spawnCorners[i]]++;
        }
    }

    static void addSpawns(List<String> commands) {
        for (int i = 0; i < spawnCorners.length; i++) {
            int corner = spawnCorners[i];
            int[] c = triHouses[spawnTriangles.get(i)];
            int a = c[0] == corner ? c[1] : c[0];
            int b = c[2] == corner ? c[1] : c[2];
            commands.add("SPAWN " + corner + " " + a + " " + b);
        }
    }

    /**
     * The corner closest to a triangle we do not own yet, where the new unit is
     * useful soonest.
     */
    static int spawnCorner(int t) {
        int best = triHouses[t][0], bestLocks = Integer.MAX_VALUE, bestDist = Integer.MAX_VALUE;
        for (int c : triHouses[t]) {
            int d = 1000;
            for (int u = 0; u < triCount; u++) {
                if (owner[u] == 0 || !meCanCapture[u])
                    continue;
                for (int k : triHouses[u])
                    d = Math.min(d, distances[c][k]);
            }
            // 生まれたユニットはその角のロックを止めるので、止める三角形が少ない角を優先する。
            int locks = lockCounts[c];
            if (locks < bestLocks || locks == bestLocks && d < bestDist) {
                bestLocks = locks;
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
