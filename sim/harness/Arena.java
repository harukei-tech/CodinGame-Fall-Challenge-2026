import com.codingame.gameengine.runner.MultiplayerGameRunner;
import com.codingame.gameengine.runner.simulate.GameResult;

import java.io.PrintStream;

/**
 * 公式の審判で1試合を行い、結果を1行で標準出力に出す。
 * 引数: シード リーグ番号 席0のコマンド 席1のコマンド
 */
public class Arena {
    public static void main(String[] args) {
        PrintStream out = System.out;
        System.setOut(System.err);
        MultiplayerGameRunner runner = new MultiplayerGameRunner();
        runner.setSeed(Long.parseLong(args[0]));
        runner.setLeagueLevel(Integer.parseInt(args[1]));
        runner.addAgent(args[2], "seat0");
        runner.addAgent(args[3], "seat1");
        GameResult result = runner.simulate();
        out.println("RESULT seed=" + args[0] + " s0=" + result.scores.get(0) + " s1=" + result.scores.get(1)
                + " tooltips=" + result.tooltips.stream().map(t -> t.text).reduce("", (a, b) -> a + b + "|"));
        System.exit(0);
    }
}
