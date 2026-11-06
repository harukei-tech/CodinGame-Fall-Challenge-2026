import java.io.*;
import java.util.*;

/**
 * Fall Challenge 2026 (TryAngle Catch) bot. Bronze league: MOVE and SPAWN only.
 *
 * Rules that drive the design (from the referee source):
 *  - A captured triangle stays ours after our units leave, so units keep moving to new triangles.
 *  - There is no combat on a house: enemies only die by being surrounded. Tying a corner is enough
 *    to stop the opponent from owning it.
 *  - SPAWN makes the triangle neutral, and we can only recapture it once our units have left all
 *    three corners.
 */
class Player {
    static final int MAX_TURNS = 200;
    /** Stop spawning this many turns before the end: a held triangle then outscores one more unit. */
    static final int SPAWN_STOP_TURNS_LEFT = 40;
    /** Once we lead by this many units, stop spawning and keep the triangles for points. */
    static final int SPAWN_UNIT_LEAD = 8;
    /** Spawn regardless of the lead before this turn. */
    static final int SPAWN_FREE_UNTIL_TURN = 60;
    static final double VALUE_NEUTRAL = 1.0;
    static final double VALUE_ENEMY = 2.2;
    static final double VALUE_DEFEND = 2.0;

    static int n;
    static int[] px, py, me, op;
    static boolean[][] adj;
    static int[][] nbr, dist;
    static int triCount;
    static int[][] tri;
    static int[] owner;
    static boolean[] meCan, opCan;

    static int[] free;
    static int[] committed;
    static int[][] moves;

    static StreamTokenizer in;

    static int next() throws IOException {
        if (in.nextToken() == StreamTokenizer.TT_EOF) System.exit(0);
        return (int) in.nval;
    }

    public static void main(String[] args) throws IOException {
        in = new StreamTokenizer(new BufferedReader(new InputStreamReader(System.in)));
        n = next();
        px = new int[n];
        py = new int[n];
        for (int i = 0; i < n; i++) {
            int id = next();
            px[id] = next();
            py[id] = next();
        }
        me = new int[n];
        op = new int[n];
        for (int turn = 1; ; turn++) {
            readTurn();
            System.out.println(decide(turn));
            System.out.flush();
        }
    }

    static void readTurn() throws IOException {
        next();
        next();
        for (int i = 0; i < n; i++) {
            int id = next();
            me[id] = next();
            op[id] = next();
        }
        adj = new boolean[n][n];
        int paths = next();
        for (int i = 0; i < paths; i++) {
            int a = next(), b = next();
            adj[a][b] = adj[b][a] = true;
        }
        triCount = next();
        tri = new int[triCount][3];
        owner = new int[triCount];
        meCan = new boolean[triCount];
        opCan = new boolean[triCount];
        for (int i = 0; i < triCount; i++) {
            for (int k = 0; k < 3; k++) tri[i][k] = next();
            owner[i] = next();
            meCan[i] = next() == 1;
            opCan[i] = next() == 1;
        }
        int linkable = next();
        for (int i = 0; i < linkable; i++) {
            next();
            next();
        }
        buildGraph();
    }

    static void buildGraph() {
        nbr = new int[n][];
        for (int i = 0; i < n; i++) {
            int c = 0;
            for (int j = 0; j < n; j++) if (adj[i][j]) c++;
            nbr[i] = new int[c];
            c = 0;
            for (int j = 0; j < n; j++) if (adj[i][j]) nbr[i][c++] = j;
        }
        dist = new int[n][n];
        int[] queue = new int[n];
        for (int s = 0; s < n; s++) {
            int[] d = dist[s];
            Arrays.fill(d, 1000);
            d[s] = 0;
            int head = 0, tail = 0;
            queue[tail++] = s;
            while (head < tail) {
                int u = queue[head++];
                for (int v : nbr[u]) {
                    if (d[v] > d[u] + 1) {
                        d[v] = d[u] + 1;
                        queue[tail++] = v;
                    }
                }
            }
        }
    }

    static String decide(int turn) {
        free = me.clone();
        committed = new int[n];
        moves = new int[n][n];

        assignTargets();
        assignLeftovers();

        List<String> commands = new ArrayList<>();
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                if (moves[from][to] > 0) commands.add("MOVE " + from + " " + to + " " + moves[from][to]);
            }
        }
        addSpawns(turn, commands);
        return commands.isEmpty() ? "WAIT" : String.join(";", commands);
    }

    /** One job: bring `required[i]` units to `houses[i]`, for a triangle to capture or defend. */
    static final class Job {
        final int[] houses;
        final int[] required;
        final double value;
        final int triangle;

        Job(int triangle, int[] houses, int[] required, double value) {
            this.triangle = triangle;
            this.houses = houses;
            this.required = required;
            this.value = value;
        }
    }

    static List<Job> buildJobs() {
        List<Job> jobs = new ArrayList<>();
        for (int t = 0; t < triCount; t++) {
            if (owner[t] != 0 && meCan[t]) {
                int[] required = new int[3];
                for (int k = 0; k < 3; k++) required[k] = op[tri[t][k]] + 1;
                jobs.add(new Job(t, tri[t], required, owner[t] == 1 ? VALUE_ENEMY : VALUE_NEUTRAL));
            } else if (owner[t] == 0 && threatened(t)) {
                int corner = cheapestBlock(t);
                jobs.add(new Job(t, new int[]{corner}, new int[]{enemyReach(corner)}, VALUE_DEFEND));
            }
        }
        return jobs;
    }

    /** Most enemy units that can stand on this house next turn. */
    static int enemyReach(int house) {
        int total = op[house];
        for (int v : nbr[house]) total += op[v];
        return total;
    }

    /** True if the opponent could own all three corners of our triangle next turn. */
    static boolean threatened(int t) {
        for (int c : tri[t]) {
            if (enemyReach(c) <= me[c]) return false;
        }
        return true;
    }

    static int cheapestBlock(int t) {
        int best = tri[t][0];
        for (int c : tri[t]) if (enemyReach(c) < enemyReach(best)) best = c;
        return best;
    }

    /** Greedily gives free units to the job with the best value for its time and travel. */
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
            if (best == null) return;
            commit(best);
            jobs.remove(best);
        }
    }

    /** Returns the job's score, or -1 if we do not have enough free units for it. */
    static double scoreJob(Job job) {
        int[] pool = free.clone();
        int time = 0, travel = 0;
        for (int i = 0; i < job.houses.length; i++) {
            int house = job.houses[i];
            int want = job.required[i] - committed[house];
            for (; want > 0; want--) {
                int from = nearestUnit(pool, house);
                if (from < 0) return -1;
                pool[from]--;
                time = Math.max(time, dist[from][house]);
                travel += dist[from][house];
            }
        }
        return job.value / (1 + time + 0.25 * travel);
    }

    static void commit(Job job) {
        for (int i = 0; i < job.houses.length; i++) {
            int house = job.houses[i];
            while (committed[house] < job.required[i]) {
                int from = nearestUnit(free, house);
                if (from < 0) return;
                free[from]--;
                committed[house]++;
                if (from != house) moves[from][stepToward(from, house)]++;
            }
        }
    }

    static int nearestUnit(int[] pool, int house) {
        int best = -1;
        for (int h = 0; h < n; h++) {
            if (pool[h] > 0 && dist[h][house] < 1000 && (best < 0 || dist[h][house] < dist[best][house])) best = h;
        }
        return best;
    }

    /** Next house on a shortest path, avoiding enemy-held houses when there is a choice. */
    static int stepToward(int from, int target) {
        int best = -1;
        for (int v : nbr[from]) {
            if (dist[v][target] == dist[from][target] - 1 && (best < 0 || op[v] < op[best])) best = v;
        }
        return best;
    }

    /** Units with no job head for the enemy, or leave houses that keep a used triangle locked. */
    static void assignLeftovers() {
        boolean[] locked = new boolean[n];
        for (int t = 0; t < triCount; t++) {
            if (owner[t] != 0 && !meCan[t]) for (int c : tri[t]) locked[c] = true;
        }
        for (int h = 0; h < n; h++) {
            while (free[h] > 0) {
                free[h]--;
                int target = leftoverTarget(h, locked);
                if (target >= 0 && target != h) {
                    moves[h][stepToward(h, target)]++;
                    committed[target]++;
                } else {
                    committed[h]++;
                }
            }
        }
    }

    static int leftoverTarget(int h, boolean[] locked) {
        int best = -1;
        for (int g = 0; g < n; g++) {
            if (op[g] > 0 && committed[g] <= op[g] && dist[h][g] < 1000 && (best < 0 || dist[h][g] < dist[h][best])) best = g;
        }
        if (best >= 0 || !locked[h]) return best;
        for (int g = 0; g < n; g++) {
            if (!locked[g] && dist[h][g] < 1000 && (best < 0 || dist[h][g] < dist[h][best])) best = g;
        }
        return best;
    }

    static void addSpawns(int turn, List<String> commands) {
        if (MAX_TURNS - turn < SPAWN_STOP_TURNS_LEFT) return;
        int mine = 0, theirs = 0;
        for (int i = 0; i < n; i++) {
            mine += me[i];
            theirs += op[i];
        }
        for (int t = 0; t < triCount; t++) {
            if (owner[t] != 0) continue;
            if (turn >= SPAWN_FREE_UNTIL_TURN && mine >= theirs + SPAWN_UNIT_LEAD) return;
            int corner = spawnCorner(t);
            int[] c = tri[t];
            int a = c[0] == corner ? c[1] : c[0];
            int b = c[2] == corner ? c[1] : c[2];
            commands.add("SPAWN " + corner + " " + a + " " + b);
            mine++;
        }
    }

    /** The corner closest to a triangle we do not own yet, where the new unit is useful soonest. */
    static int spawnCorner(int t) {
        int best = tri[t][0], bestDist = Integer.MAX_VALUE;
        for (int c : tri[t]) {
            int d = 1000;
            for (int u = 0; u < triCount; u++) {
                if (owner[u] == 0 || !meCan[u]) continue;
                for (int k : tri[u]) d = Math.min(d, dist[c][k]);
            }
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }
}
