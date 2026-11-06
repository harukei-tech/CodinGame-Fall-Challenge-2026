import java.io.*;
import java.net.Socket;

/** 審判の標準入出力を、boss コンテナのTCP接続へ中継する。 */
public class BossClient {
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket(args[0], Integer.parseInt(args[1]));
        socket.setTcpNoDelay(true);
        Thread toBoss = new Thread(() -> pipe(System.in, socket));
        toBoss.setDaemon(true);
        toBoss.start();
        InputStream fromBoss = socket.getInputStream();
        byte[] buffer = new byte[65536];
        for (int read; (read = fromBoss.read(buffer)) >= 0; ) {
            System.out.write(buffer, 0, read);
            System.out.flush();
        }
        System.exit(0);
    }

    static void pipe(InputStream in, Socket socket) {
        try {
            OutputStream out = socket.getOutputStream();
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) >= 0; ) {
                out.write(buffer, 0, read);
                out.flush();
            }
            socket.close();
        } catch (IOException e) {
            System.exit(0);
        }
    }
}
