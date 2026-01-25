import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * @author Brandon Kuciapski
 * Swing GUI wrapper for the blockchain node
 */
public class GUIframe extends JFrame {

    private JTextArea terminal;
    private JTextField portField;

    private Node node;

    public GUIframe() {
        setTitle("Chintcoin GUI Blockchain Client");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(createTopPanel(), BorderLayout.NORTH);
        add(createLeftPanel(), BorderLayout.WEST);
        add(createTerminalPanel(), BorderLayout.CENTER);

        redirectSystemOutput();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private Font loadFont(String path, float size) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("Font not found: " + path);
                return new Font("SansSerif", Font.PLAIN, (int) size);
            }

            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);

            return font.deriveFont(size);

        } catch (Exception e) {
            e.printStackTrace();
            return new Font("SansSerif", Font.PLAIN, (int) size);
        }
    }


    // ───────────────────────── TOP PANEL ────────────────────────
    private JPanel createTopPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        // ── Title Bar ──
        JPanel titleBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titleBar.setBackground(new Color(20, 20, 20));
        titleBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // Optional logo
//        JLabel logo = new JLabel();
//        ImageIcon icon = loadLogo("logo.png");
//        if (icon != null) {
//            logo.setIcon(icon);
//            titleBar.add(logo);
//        }

        JLabel title = new JLabel("CHINTCOIN client");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleBar.add(title);

        Font titleFont = loadFont("/fonts/ITCAvantGardeStd-Bk.ttf", 22f);
        title.setFont(titleFont);


        // ── Control Bar ──
        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlBar.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        controlBar.add(new JLabel("Port:"));
        portField = new JTextField(6);
        controlBar.add(portField);
        controlBar.setBackground(new Color(80, 80, 80));
        controlBar.setForeground(Color.WHITE);

        JButton startButton = new JButton("Start Node");
        startButton.addActionListener(e -> startNode());
        controlBar.add(startButton);

        container.add(titleBar);
        container.add(controlBar);

        return container;
    }


    // ───────────────────────── LEFT PANEL ─────────────────────────
    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setPreferredSize(new Dimension(160, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(30, 30, 30));


        panel.add(button("Help", this::help));
        panel.add(button("Connect", this::connect));
        panel.add(button("Peers", this::peers));
        panel.add(button("Send", this::send));
        panel.add(button("Balance", this::balance));
        panel.add(button("Chain", this::chain));
        panel.add(button("UTXO", this::utxo));
        panel.add(button("Mempool", this::mempool));
        panel.add(button("Mine", this::mine));
        panel.add(button("Exit", this::exit));

        return panel;
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());

        b.setFocusPainted(false);
        b.setBackground(new Color(45, 45, 45));
//        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        return b;
    }

    // ───────────────────────── TERMINAL ─────────────────────────
    private JScrollPane createTerminalPanel() {
        terminal = new JTextArea();
        terminal.setEditable(false);
        terminal.setLineWrap(true);
        terminal.setWrapStyleWord(true);
        terminal.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));

        terminal.setBackground(Color.BLACK);
        terminal.setForeground(new Color(0x33FF33));

        JScrollPane scroll = new JScrollPane(terminal);
        scroll.setPreferredSize(new Dimension(0, 400));
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scroll.setBackground(new Color(80, 80, 80));

        return scroll;
    }


    private void redirectSystemOutput() {
        PrintStream ps = new PrintStream(new TextAreaOutputStream(terminal));
        System.setOut(ps);
        System.setErr(ps);
    }

    // ───────────────────────── NODE CONTROL ─────────────────────────
    private void startNode() {
        if (node != null) {
            System.out.println("Node already running.");
            return;
        }

        try {
            int port = Integer.parseInt(portField.getText());
            node = new Node(port);
            node.start();

            System.out.println("Node running on port " + port);
            System.out.println("Public key:");
            System.out.println(node.getPublicKeyBase64());
            System.out.println("Type 'help' using the buttons.");

        } catch (Exception e) {
            System.out.println("Failed to start node: " + e.getMessage());
        }
    }

    // ───────────────────────── COMMANDS ─────────────────────────
    private void help() {
        System.out.println("""
        Commands:
          help                    Show this help
          connect <ip> <port>     Connect to another node
          peers                   List connected peers
          send <pubKey> <amount>  Create + broadcast transaction
          balance                 Show this node's balance
          chain                   Print blockchain summary
          utxo                    Print UTXO set (balances)
          mempool                 Show pending transactions
          mine                    Mine block from mempool
          exit                    Shutdown node
        """);
    }

    private void connect() {
        if (node == null) return;

        String ip = JOptionPane.showInputDialog(this, "Peer IP:");
        String port = JOptionPane.showInputDialog(this, "Peer Port:");

        if (ip != null && port != null) {
            node.syncWithPeer(ip, Integer.parseInt(port));
        }
    }

    private void peers() {
        if (node != null) node.printPeers();
    }

    private void send() {
        if (node == null) return;

        String pubKey = JOptionPane.showInputDialog(this, "Receiver Public Key (Base64):");
        String amount = JOptionPane.showInputDialog(this, "Amount:");

        if (pubKey == null || amount == null) return;

        try {
            long amt = Long.parseLong(amount);

            Transaction tx = node.createTransaction(
                    java.security.KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new java.security.spec.X509EncodedKeySpec(
                                            java.util.Base64.getDecoder().decode(pubKey)
                                    )
                            ),
                    amt
            );

            node.addTransactionToMempool(tx);
            node.broadcastTransaction(tx);

            System.out.println("Transaction created: " + tx.txId);

        } catch (Exception e) {
            System.out.println("Failed to send transaction: " + e.getMessage());
        }
    }

    private void balance() {
        if (node != null)
            System.out.println("Balance: " + node.getBalance());
    }

    private void chain() {
        if (node != null)
            node.getBlockchain().printChain();
    }

    private void utxo() {
        if (node != null)
            node.printUTXO();
    }

    private void mempool() {
        if (node != null)
            node.printMempool();
    }

    private void mine() {
        if (node != null) {
            try {
                node.mineFromMempool();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void exit() {
        if (node != null)
            node.disconnect();
        System.exit(0);
    }
}
