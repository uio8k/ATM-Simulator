import java.io.*;
import java.net.*;

/**
 * ATM客户端模拟程序（支持配置服务器IP和端口）
 * 用法: java ATMClient [serverIP] [port]
 * 默认: 127.0.0.1  2525
 */
public class ATMClient {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 2525;

    public static void main(String[] args) {
        // 解析服务器地址和端口
        String serverHost = DEFAULT_HOST;
        int serverPort = DEFAULT_PORT;

        if (args.length >= 1) {
            serverHost = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
                if (serverPort < 1024 || serverPort > 65535) {
                    System.err.println("端口号范围应为 1024~65535，将使用默认端口 " + DEFAULT_PORT);
                    serverPort = DEFAULT_PORT;
                }
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，将使用默认端口 " + DEFAULT_PORT);
            }
        }

        try (Socket socket = new Socket(serverHost, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("已连接到ATM服务器 " + serverHost + ":" + serverPort);
            System.out.print("请输入卡号: ");
            System.out.flush();
            String cardNo = console.readLine();
            out.println("HELO " + cardNo);
            String response = in.readLine();
            if (!response.startsWith("500")) {
                System.out.println("服务器错误: " + response);
                return;
            }
            System.out.println("服务器要求验证口令: " + response);
            System.out.print("请输入口令: ");
            System.out.flush();
            String passwd = console.readLine();
            out.println("PASS " + passwd);
            response = in.readLine();
            if (!"525 OK!".equals(response)) {
                System.out.println("认证失败: " + response);
                return;
            }
            System.out.println("认证成功！");

            while (true) {
                System.out.println("\n可选操作: BALA(查询余额)  WDRA <金额>  QUIT(退出)");
                System.out.flush();
                System.out.print("> ");
                String input = console.readLine();
                if (input == null) break;
                out.println(input);
                String resp = in.readLine();
                if (resp == null) break;

                if (resp.startsWith("AMNT:")) {
                    System.out.println("当前余额: " + resp.substring(5) + " 元");
                } else if ("525 OK!".equals(resp)) {
                    System.out.println("操作成功！");
                } else if ("401 ERROR!".equals(resp)) {
                    System.out.println("操作失败！请检查卡号、口令或余额是否充足。");
                } else if ("BYE".equals(resp)) {
                    System.out.println("服务器已结束会话，感谢使用。");
                    break;
                } else {
                    System.out.println("未知响应: " + resp);
                }
                if ("QUIT".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("客户端异常: " + e.getMessage());
        }
    }
}
