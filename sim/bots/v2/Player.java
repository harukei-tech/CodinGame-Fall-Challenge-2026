import java.io.*;
import java.util.*;

/**
 * Fall Challenge 2026 (TryAngle Catch) bot. Uses MOVE and SPAWN.
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
    static final double VALUE_ENEMY = 2.2;
    static final double VALUE_DEFEND = 2.0;
    /** 敵を包囲する仕事の価値。倒せる敵ユニット1体あたりの値。 */
    static final double VALUE_SURROUND_PER_UNIT = 1.0;
    /** 隣の家がこの数より多い敵は、囲むのに手間がかかるので狙わない。 */
    static final int SURROUND_MAX_NEIGHBORS = 4;
    /** 敵は動くので、この移動ターン数以内に囲める場合だけ狙う。 */
    static final int SURROUND_MAX_TIME = 2;
    /** 余りユニットは、この距離以内に敵がいれば、ロック解除より敵を優先する。 */
    static final int LEFTOVER_ENEMY_CLOSE_DISTANCE = 2;
    /** 包囲対策に使える時間。1ターン50msの制限に余裕を残す。 */
    static final long SURROUND_CHECK_BUDGET_NANOS = 25_000_000L;

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
            logTurn(turn);
            System.out.println(decide(turn));
            System.out.flush();
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
        int linkable = next();
        for (int i = 0; i < linkable; i++) {
            next();
            next();
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
        free = myUnits.clone();
        committed = new int[houseCount];
        moves = new int[houseCount][houseCount];

        assignTargets();
        assignLeftovers();
        avoidSurrounds();

        List<String> commands = new ArrayList<>();
        for (int from = 0; from < houseCount; from++) {
            for (int to = 0; to < houseCount; to++) {
                if (moves[from][to] > 0)
                    commands.add("MOVE " + from + " " + to + " " + moves[from][to]);
            }
        }
        addSpawns(turn, commands);
        return commands.isEmpty() ? "WAIT" : String.join(";", commands);
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
                for (int k = 0; k < 3; k++)
                    required[k] = opponentUnits[triHouses[t][k]] + 1;
                jobs.add(new Job(t, triHouses[t], required, owner[t] == 1 ? VALUE_ENEMY : VALUE_NEUTRAL));
            } else if (owner[t] == 0 && threatened(t)) {
                int corner = cheapestBlock(t);
                jobs.add(new Job(t, new int[] { corner }, new int[] { enemyReach(corner) }, VALUE_DEFEND));
            }
        }
        addSurroundJobs(jobs);
        return jobs;
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
            if (pool[h] > 0 && distances[h][house] < UNREACHABLE && (best < 0 || distances[h][house] < distances[best][house]))
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
        // SPAWNで使った三角形は、3つの角から自分のユニットが全員離れるまで再占領できない。
        // そのような三角形の角を「ロック中の家」として記録する。
        // （敵の三角形は常に奪えるので、ここに当てはまるのは中立の三角形だけ。）
        boolean[] isLockedHouse = new boolean[houseCount];
        for (int triangle = 0; triangle < triCount; triangle++) {
            if (owner[triangle] != 0 && !meCanCapture[triangle])
                for (int corner : triHouses[triangle])
                    isLockedHouse[corner] = true;
        }

        for (int house = 0; house < houseCount; house++) {
            while (free[house] > 0) {
                free[house]--;
                int target = leftoverTarget(house, isLockedHouse);
                if (target >= 0 && target != house) {
                    // 今ターンは隣の家へ1歩だけ進み、次のターンにまた行き先を決め直す。
                    moves[house][stepToward(house, target)]++;
                    committed[target]++;
                } else {
                    // 行き先がない、または今いる家が行き先なら、その場に残る。
                    committed[house]++;
                }
            }
        }
    }

    /**
     * houseにいる余りユニットの行き先を返す。行き先がなければ-1を返す。
     * 優先順位:
     * 1. 最も近い「敵がいて、自軍の割り当てがまだ敵の数を超えていない家」
     * 2. 1がなく、今いる家がロック中なら、最も近い「ロック中でない家」
     */
    static int leftoverTarget(int house, boolean[] isLockedHouse) {
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
        if (enemyIsClose || !isLockedHouse[house])
            return best;

        // 近くに敵がいないので、三角形を再占領できるようにロック中の家から離れる。
        int enemyTarget = best;
        best = -1;
        for (int candidate = 0; candidate < houseCount; candidate++) {
            boolean reachable = distances[house][candidate] < UNREACHABLE;
            if (!isLockedHouse[candidate] && reachable
                    && (best < 0 || distances[house][candidate] < distances[house][best]))
                best = candidate;
        }
        return best >= 0 ? best : enemyTarget;
    }

    /**
     * 立てた移動計画を見直し、次のターンに包囲されるユニットが減るように修正する。
     * ユニット1体の行き先を変える案をすべて試し、最も危険が減る案を採用する。
     * 危険が減らなくなるか、時間がなくなったら終わる。
     */
    static void avoidSurrounds() {
        int[] projected = projectedUnits();
        int risk = unitsAtRisk(projected);
        while (risk > 0 && System.nanoTime() - turnStartNanos < SURROUND_CHECK_BUDGET_NANOS) {
            int bestFrom = -1, bestOldTo = -1, bestNewTo = -1, bestRisk = risk;
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

    /** 今ターンの移動後に、各家にいる自分のユニット数を返す。 */
    static int[] projectedUnits() {
        int[] projected = myUnits.clone();
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

    static void addSpawns(int turn, List<String> commands) {
        if (MAX_TURNS - turn < SPAWN_STOP_TURNS_LEFT)
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
                return;
            int corner = spawnCorner(t);
            int[] c = triHouses[t];
            int a = c[0] == corner ? c[1] : c[0];
            int b = c[2] == corner ? c[1] : c[2];
            commands.add("SPAWN " + corner + " " + a + " " + b);
            mine++;
        }
    }

    /**
     * The corner closest to a triangle we do not own yet, where the new unit is
     * useful soonest.
     */
    static int spawnCorner(int t) {
        int best = triHouses[t][0], bestDist = Integer.MAX_VALUE;
        for (int c : triHouses[t]) {
            int d = 1000;
            for (int u = 0; u < triCount; u++) {
                if (owner[u] == 0 || !meCanCapture[u])
                    continue;
                for (int k : triHouses[u])
                    d = Math.min(d, distances[c][k]);
            }
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
