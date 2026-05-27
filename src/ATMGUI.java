import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ATM图形界面客户端
 * 用法: java ATMGUI [serverIP] [port]
 * 默认: 127.0.0.1  2525
 */
public class ATMGUI {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 2525;
    // 日志
    private static final String LOG_FILE = "atm-client.log";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private JFrame frame;
    private JPanel mainPanel;
    private CardLayout cardLayout;

    // 连接界面
    private JTextField hostField;
    private JTextField portField;

    // 登录界面
    private JTextField cardField;
    private JPasswordField passField;
    private JLabel loginStatusLabel;
    private String currentCard;

    // 主功能界面
    private JLabel balanceLabel;
    private JTextField withdrawField;
    private JLabel operationStatusLabel;

    /** 写入客户端日志 */
    private static synchronized void log(String msg) {
        String timestamp = sdf.format(new Date());
        String line = "[" + timestamp + "] " + msg;
        System.out.println(line);
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println(line);
        } catch (IOException e) {
            System.err.println("写入客户端日志失败: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); } catch (Exception ignored) {}
        }
        new ATMGUI(host, port);
    }

    public ATMGUI(String defaultHost, int defaultPort) {
        createUI(defaultHost, defaultPort);
    }

    private void createUI(String defaultHost, int defaultPort) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        frame = new JFrame("ATM 客户端");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 380);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(createConnectPanel(defaultHost, defaultPort), "connect");
        mainPanel.add(createLoginPanel(), "login");
        mainPanel.add(createMainPanel(), "main");

        cardLayout.show(mainPanel, "connect");
        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // ========== 连接界面 ==========
    private JPanel createConnectPanel(String defaultHost, int defaultPort) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 20, 8, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题
        JLabel title = new JLabel("ATM 客户端", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 22));
        title.setForeground(new Color(33, 150, 243));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        // 子标题
        JLabel subtitle = new JLabel("请连接到服务器", SwingConstants.CENTER);
        subtitle.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        subtitle.setForeground(Color.GRAY);
        gbc.gridy = 1;
        panel.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 20, 5, 20);

        // 服务器地址
        gbc.gridy = 2; gbc.gridx = 0;
        panel.add(new JLabel("服务器地址:"), gbc);
        hostField = new JTextField(defaultHost, 15);
        hostField.setFont(new Font("Consolas", Font.PLAIN, 13));
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        // 端口
        gbc.gridy = 3; gbc.gridx = 0;
        panel.add(new JLabel("端口号:"), gbc);
        portField = new JTextField(String.valueOf(defaultPort), 15);
        portField.setFont(new Font("Consolas", Font.PLAIN, 13));
        gbc.gridx = 1;
        panel.add(portField, gbc);

        // 连接按钮
        JButton connectBtn = new JButton("连接服务器");
        connectBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        connectBtn.setBackground(new Color(33, 150, 243));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setFocusPainted(false);
        connectBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        connectBtn.addActionListener(e -> connect());
        gbc.gridy = 4; gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 20, 5, 20);
        panel.add(connectBtn, gbc);

        return panel;
    }

    // ========== 登录界面 ==========
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 20, 8, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("用户登录", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setForeground(new Color(33, 150, 243));
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(title, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 20, 5, 20);

        gbc.gridy = 1; gbc.gridx = 0;
        panel.add(new JLabel("卡号:"), gbc);
        cardField = new JTextField(15);
        cardField.setFont(new Font("Consolas", Font.PLAIN, 14));
        cardField.addActionListener(e -> passField.requestFocus());
        gbc.gridx = 1;
        panel.add(cardField, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        panel.add(new JLabel("口令:"), gbc);
        passField = new JPasswordField(15);
        passField.setFont(new Font("Consolas", Font.PLAIN, 14));
        passField.addActionListener(e -> login());
        gbc.gridx = 1;
        panel.add(passField, gbc);

        loginStatusLabel = new JLabel("", SwingConstants.CENTER);
        loginStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        gbc.gridy = 3; gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(loginStatusLabel, gbc);

        JButton loginBtn = new JButton("登  录");
        loginBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        loginBtn.setBackground(new Color(76, 175, 80));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        loginBtn.addActionListener(e -> login());
        gbc.gridy = 4; gbc.insets = new Insets(10, 20, 5, 20);
        panel.add(loginBtn, gbc);

        JButton backBtn = new JButton("返回");
        backBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        backBtn.setBorder(new EmptyBorder(4, 15, 4, 15));
        backBtn.addActionListener(e -> {
            disconnect();
            cardLayout.show(mainPanel, "connect");
        });
        gbc.gridy = 5; gbc.insets = new Insets(0, 20, 10, 20);
        panel.add(backBtn, gbc);

        return panel;
    }

    // ========== 主功能界面 ==========
    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 20, 6, 20);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 2;

        JLabel title = new JLabel("ATM 主菜单", SwingConstants.CENTER);
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));
        title.setForeground(new Color(33, 150, 243));
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(title, gbc);

        // 余额显示
        balanceLabel = new JLabel("余额: --", SwingConstants.CENTER);
        balanceLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        balanceLabel.setForeground(new Color(0, 150, 136));
        balanceLabel.setBorder(new CompoundBorder(
            new LineBorder(new Color(0, 150, 136), 1, true),
            new EmptyBorder(10, 0, 10, 0)
        ));
        gbc.gridy = 1;
        gbc.insets = new Insets(10, 20, 10, 20);
        panel.add(balanceLabel, gbc);

        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 20, 5, 20);

        // 查询余额按钮
        JButton balaBtn = new JButton("查询余额");
        balaBtn.setFont(new Font("微软雅黑", Font.BOLD, 13));
        balaBtn.setBackground(new Color(0, 150, 136));
        balaBtn.setForeground(Color.WHITE);
        balaBtn.setFocusPainted(false);
        balaBtn.addActionListener(e -> doBala());
        gbc.gridy = 2; gbc.gridx = 0;
        panel.add(balaBtn, gbc);

        // 取款按钮
        JButton wdraBtn = new JButton("取款");
        wdraBtn.setFont(new Font("微软雅黑", Font.BOLD, 13));
        wdraBtn.setBackground(new Color(255, 152, 0));
        wdraBtn.setForeground(Color.WHITE);
        wdraBtn.setFocusPainted(false);
        wdraBtn.addActionListener(e -> doWdra());
        gbc.gridx = 1;
        panel.add(wdraBtn, gbc);

        // 取款金额输入
        gbc.gridy = 3; gbc.gridx = 0;
        gbc.gridwidth = 2;
        withdrawField = new JTextField();
        withdrawField.setFont(new Font("Consolas", Font.PLAIN, 14));
        withdrawField.setHorizontalAlignment(JTextField.CENTER);
        withdrawField.setBorder(new TitledBorder("取款金额"));
        withdrawField.addActionListener(e -> doWdra());
        panel.add(withdrawField, gbc);

        // 操作状态
        operationStatusLabel = new JLabel("", SwingConstants.CENTER);
        operationStatusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        gbc.gridy = 4; gbc.insets = new Insets(0, 20, 5, 20);
        panel.add(operationStatusLabel, gbc);

        // 退出按钮
        JButton quitBtn = new JButton("退出登录");
        quitBtn.setFont(new Font("微软雅黑", Font.BOLD, 13));
        quitBtn.setBackground(new Color(244, 67, 54));
        quitBtn.setForeground(Color.WHITE);
        quitBtn.setFocusPainted(false);
        quitBtn.addActionListener(e -> doQuit());
        gbc.gridy = 5; gbc.gridwidth = 2;
        gbc.insets = new Insets(5, 20, 10, 20);
        panel.add(quitBtn, gbc);

        return panel;
    }

    // ========== 网络通信 ==========

    private void connect() {
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "端口号格式错误", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            log("连接到服务器 " + host + ":" + port + " 成功");
            cardLayout.show(mainPanel, "login");
            loginStatusLabel.setText("已连接到 " + host + ":" + port);
            loginStatusLabel.setForeground(new Color(76, 175, 80));
            cardField.requestFocus();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "连接失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void login() {
        String card = cardField.getText().trim();
        String pass = new String(passField.getPassword());

        if (card.isEmpty() || pass.isEmpty()) {
            loginStatusLabel.setText("请输入卡号和口令");
            loginStatusLabel.setForeground(Color.RED);
            return;
        }

        try {
            out.println("HELO " + card);
            String resp = in.readLine();
            if (!"500 AUTH REQUIRE".equals(resp)) {
                log("登录失败 - 卡号 " + card + " 不存在");
                loginStatusLabel.setText("卡号不存在，请重试");
                loginStatusLabel.setForeground(Color.RED);
                return;
            }
            out.println("PASS " + pass);
            resp = in.readLine();
            if ("525 OK!".equals(resp)) {
                currentCard = card;
                log("卡号 " + card + " 登录成功");
                cardLayout.show(mainPanel, "main");
                balanceLabel.setText("余额: --");
                operationStatusLabel.setText("登录成功！卡号: " + card);
                operationStatusLabel.setForeground(new Color(76, 175, 80));
                withdrawField.setText("");
            } else {
                log("卡号 " + card + " 口令错误");
                loginStatusLabel.setText("口令错误，请重试");
                loginStatusLabel.setForeground(Color.RED);
                passField.setText("");
                passField.requestFocus();
            }
        } catch (IOException ex) {
            loginStatusLabel.setText("通信异常: " + ex.getMessage());
            loginStatusLabel.setForeground(Color.RED);
        }
    }

    private void doBala() {
        try {
            out.println("BALA");
            String resp = in.readLine();
            if (resp != null && resp.startsWith("AMNT:")) {
                String amt = resp.substring(5);
                balanceLabel.setText("余额: " + amt + " 元");
                log("卡号 " + currentCard + " 查询余额: " + amt);
                operationStatusLabel.setText("查询成功");
                operationStatusLabel.setForeground(new Color(76, 175, 80));
            } else {
                operationStatusLabel.setText("查询失败: " + resp);
                operationStatusLabel.setForeground(Color.RED);
            }
        } catch (IOException ex) {
            operationStatusLabel.setText("通信异常");
            operationStatusLabel.setForeground(Color.RED);
        }
    }

    private void doWdra() {
        String amtText = withdrawField.getText().trim();
        if (amtText.isEmpty()) {
            operationStatusLabel.setText("请输入取款金额");
            operationStatusLabel.setForeground(Color.RED);
            return;
        }
        try {
            double amt = Double.parseDouble(amtText);
            if (amt <= 0) {
                operationStatusLabel.setText("金额必须大于0");
                operationStatusLabel.setForeground(Color.RED);
                return;
            }
            out.println("WDRA " + amtText);
            String resp = in.readLine();
            if ("525 OK!".equals(resp)) {
                log("卡号 " + currentCard + " 取款 " + amtText + " 元成功");
                operationStatusLabel.setText("取款 " + amtText + " 元成功");
                operationStatusLabel.setForeground(new Color(76, 175, 80));
                withdrawField.setText("");
                // 自动刷新余额
                doBala();
            } else {
                operationStatusLabel.setText("取款失败，请检查余额是否充足");
                operationStatusLabel.setForeground(Color.RED);
            }
        } catch (NumberFormatException e) {
            operationStatusLabel.setText("金额格式错误");
            operationStatusLabel.setForeground(Color.RED);
        } catch (IOException ex) {
            operationStatusLabel.setText("通信异常");
            operationStatusLabel.setForeground(Color.RED);
        }
    }

    private void doQuit() {
        try {
            out.println("QUIT");
            in.readLine();
        } catch (IOException ignored) {}
        log("卡号 " + currentCard + " 退出登录");
        disconnect();
        currentCard = null;
        cardField.setText("");
        passField.setText("");
        loginStatusLabel.setText("");
        cardLayout.show(mainPanel, "connect");
    }

    private void disconnect() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}
