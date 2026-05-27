import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ATM服务器端（支持端口号配置）
 * 用法: java ATMServer [port]
 * 默认端口: 2525
 */
public class ATMServer {
    private static final int DEFAULT_PORT = 2525;
    // 存储用户口令: cardNo -> password
    private static final Map<String, String> userPasswords = new ConcurrentHashMap<>();
    // 存储用户余额: cardNo -> balance
    private static final Map<String, Double> userBalances = new ConcurrentHashMap<>();
    // 日志文件
    private static final String LOG_FILE = "atm-server.log";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        // 解析端口号
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1024 || port > 65535) {
                    System.err.println("端口号范围应为 1024~65535，将使用默认端口 " + DEFAULT_PORT);
                    port = DEFAULT_PORT;
                }
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号，将使用默认端口 " + DEFAULT_PORT);
            }
        }

        loadUserData();
        log("ATM服务器启动，监听端口 " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("ATM服务器已就绪，等待连接...");
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 从users.txt和balances.txt加载数据 */
    private static void loadUserData() {
        // 加载口令
        try (BufferedReader br = new BufferedReader(new FileReader("users.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    userPasswords.put(parts[0], parts[1]);
                }
            }
            log("已加载 " + userPasswords.size() + " 个用户账号");
        } catch (IOException e) {
            log("读取users.txt失败，将使用空数据集");
        }

        // 加载余额
        try (BufferedReader br = new BufferedReader(new FileReader("balances.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    try {
                        userBalances.put(parts[0], Double.parseDouble(parts[1]));
                    } catch (NumberFormatException ignored) {}
                }
            }
            log("已加载 " + userBalances.size() + " 个余额记录");
        } catch (IOException e) {
            log("读取balances.txt失败，将使用空数据集");
        }
    }

    /** 写入日志 */
    private static synchronized void log(String msg) {
        String timestamp = sdf.format(new Date());
        String line = "[" + timestamp + "] " + msg;
        System.out.println(line);
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println(line);
        } catch (IOException e) {
            System.err.println("写入日志失败: " + e.getMessage());
        }
    }

    /** 更新balances.txt文件（将所有当前余额写回） */
    private static synchronized void saveBalances() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("balances.txt"))) {
            for (Map.Entry<String, Double> entry : userBalances.entrySet()) {
                pw.printf("%s %.2f%n", entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            log("保存balances.txt失败: " + e.getMessage());
        }
    }

    /** 处理单个客户端连接的线程 */
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final String clientAddr;
        private String currentCard = null;
        private boolean authenticated = false;

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.clientAddr = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
            log("客户端已连接: " + clientAddr);
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                String line;
                while ((line = in.readLine()) != null) {
                    log("[客户端 " + clientAddr + "] 收到: " + line);
                    String response = processCommand(line.trim());
                    out.println(response);
                    log("[客户端 " + clientAddr + "] 回复: " + response);
                    if (response.equals("BYE")) break;
                }
            } catch (IOException e) {
                log("客户端连接异常 [" + clientAddr + "]: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
                log("客户端断开 [" + clientAddr + "]，卡号: " + (currentCard == null ? "未知" : currentCard));
            }
        }

        private String processCommand(String cmd) {
            String[] parts = cmd.split("\\s+", 2);
            String command = parts[0].toUpperCase();

            switch (command) {
                case "HELO":
                    if (parts.length < 2) return "401 ERROR!";
                    currentCard = parts[1];
                    if (userPasswords.containsKey(currentCard)) {
                        authenticated = false;
                        return "500 AUTH REQUIRE";
                    } else {
                        currentCard = null;
                        return "401 ERROR!";
                    }
                case "PASS":
                    if (currentCard == null || parts.length < 2) return "401 ERROR!";
                    String pwd = parts[1];
                    if (userPasswords.getOrDefault(currentCard, "").equals(pwd)) {
                        authenticated = true;
                        return "525 OK!";
                    } else {
                        return "401 ERROR!";
                    }
                case "BALA":
                    if (!authenticated) return "401 ERROR!";
                    Double bal = userBalances.get(currentCard);
                    return bal == null ? "401 ERROR!" : String.format("AMNT:%.2f", bal);
                case "WDRA":
                    if (!authenticated || parts.length < 2) return "401 ERROR!";
                    double amount;
                    try {
                        amount = Double.parseDouble(parts[1]);
                        if (amount <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        return "401 ERROR!";
                    }
                    Double currentBalance = userBalances.get(currentCard);
                    if (currentBalance == null) return "401 ERROR!";
                    if (currentBalance >= amount) {
                        userBalances.put(currentCard, currentBalance - amount);
                        saveBalances();
                        return "525 OK!";
                    } else {
                        return "401 ERROR!";
                    }
                case "QUIT":
                    authenticated = false;
                    currentCard = null;
                    return "BYE";
                default:
                    return "401 ERROR!";
            }
        }
    }
}
