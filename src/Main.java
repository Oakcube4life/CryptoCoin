/*
 * Gavin MacFadyen
 *
 * Simple interface to host and connect to simulate the blockchain.
 * This was made with one GPT prompt lol.
*/
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java Main <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Node node = new Node(port);
        node.start();

        Scanner scanner = new Scanner(System.in);

        System.out.println("Node running on port " + port);
        System.out.println("Public key:");
        System.out.println(node.getPublicKeyBase64());
        System.out.println("Type 'help' for commands.");

        while (true) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");

            switch (parts[0].toLowerCase()) {
                case "help" -> printHelp();
                case "connect" -> {
                    if (parts.length != 3) {
                        System.out.println("Usage: connect <host> <port>");
                        break;
                    }
                    node.syncWithPeer(parts[1], Integer.parseInt(parts[2]));
                }
                case "send" -> {
                    if (parts.length != 3) {
                        System.out.println("Usage: send <receiverPubKeyBase64> <amount>");
                        break;
                    }

                    PublicKey recipient = KeyFactory.getInstance("RSA")
                            .generatePublic(new X509EncodedKeySpec(
                                    Base64.getDecoder().decode(parts[1])
                            ));

                    long amount = Long.parseLong(parts[2]);

                    try {
                        Transaction tx = node.createTransaction(recipient, amount);
                        node.addTransactionToMempool(tx);
                        node.broadcastTransaction(tx);

                        System.out.println("Transaction created: " + tx.txId);

                    } catch (Exception e) {
                        System.out.println("Failed to create transaction: " + e.getMessage());
                    }
                }
                case "peers" -> node.printPeers();
                case "chain" -> node.getBlockchain().printChain();
                case "utxo" -> node.printUTXO();
                case "mempool" -> node.printMempool();
                case "mine" -> node.mineFromMempool();
                case "balance" -> {
                    long bal = node.getBalance();
                    System.out.println("Balance: " + bal);
                }
                case "exit" -> {
                    node.disconnect();
                    System.exit(0);
                }
                default -> System.out.println("Unknown command. Type 'help'.");
            }
        }
    }

    private static void printHelp() {
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
}