import com.codingame.gameengine.runner.MultiplayerGameRunner;
import com.codingame.gameengine.runner.simulate.GameResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公式の審判で1試合を行い、結果を1行で標準出力に出す。
 * 引数: シード リーグ番号 席0のコマンド 席1のコマンド
 * 環境変数 STDERR_DIR を指定すると、各席の標準エラーを <シード>-<席>.txt に保存する。
 */
public class Arena {
    public static void main(String[] args) throws IOException {
        PrintStream out = System.out;
        System.setOut(System.err);
        MultiplayerGameRunner runner = new MultiplayerGameRunner();
        runner.setSeed(Long.parseLong(args[0]));
        runner.setLeagueLevel(Integer.parseInt(args[1]));
        runner.addAgent(args[2], "seat0");
        runner.addAgent(args[3], "seat1");
        GameResult result = runner.simulate();
        saveErrors(args[0], result.errors);
        saveOutputs(args[0], result.outputs);
        out.println("RESULT seed=" + args[0] + " s0=" + result.scores.get(0) + " s1=" + result.scores.get(1)
                + " tooltips=" + result.tooltips.stream().map(t -> t.text).reduce("", (a, b) -> a + b + "|"));
        System.exit(0);
    }

    private static void saveOutputs(String seed, Map<String, List<String>> outputs) throws IOException {
        String directory = System.getenv("STDERR_DIR");
        if (directory == null)
            return;
        for (Map.Entry<String, List<String>> seat : outputs.entrySet()) {
            Path file = Paths.get(directory, seed + "-" + seat.getKey() + ".out");
            String text = seat.getValue().stream().filter(line -> line != null).collect(Collectors.joining("\n"));
            Files.write(file, text.getBytes("UTF-8"));
        }
    }

    private static void saveErrors(String seed, Map<String, List<String>> errors) throws IOException {
        String directory = System.getenv("STDERR_DIR");
        if (directory == null)
            return;
        Files.createDirectories(Paths.get(directory));
        for (Map.Entry<String, List<String>> seat : errors.entrySet()) {
            Path file = Paths.get(directory, seed + "-" + seat.getKey() + ".txt");
            String text = seat.getValue().stream().filter(line -> line != null).collect(Collectors.joining("\n"));
            Files.write(file, text.getBytes("UTF-8"));
        }
    }
}
