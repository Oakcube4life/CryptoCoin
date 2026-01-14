/*
 * Gavin MacFadyen
 *
 * Simple interface to host and connect to simulate the blockchain.
 * This was made with one GPT prompt lol.
*/
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
        System.out.println("Type 'help' for commands.");
        System.out.println("My public key: " + node.getPublicKeyBase64());

        while (true) {
            System.out.print("");
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");

            if (parts.length == 0) continue;

            switch (parts[0].toLowerCase()) {
                case "help":
                    printHelp();
                    break;
                case "connect":
                    if (parts.length != 3) {
                        System.out.println("Usage: connect <host> <port>");
                        break;
                    }

                    String host = parts[1];
                    int peerPort = Integer.parseInt(parts[2]);

                    node.syncWithPeer(host, peerPort);
                    System.out.println("Connected to " + host + ":" + peerPort);
                    break;
                case "peers":
                    node.printPeers();
                    break;
                case "chain":
                    node.getBlockchain().printChain();
                    break;
                case "mempool":
                    node.printMempool();
                    break;
                case "send":
                    if (parts.length != 3) {
                        System.out.println("Usage: send <receiverPublicKey> <amount>");
                        break;
                    }

                    String receiver = parts[1];
                    int amount = Integer.parseInt(parts[2]);

                    Transaction tx = node.createTransaction(receiver, amount);
                    node.addTransactionToMempool(tx);
                    node.broadcastTransaction(tx);

                    System.out.println("Transaction sent: " + tx.txId);
                    break;
                case "mine":
                    node.mineFromMempool();
                    break;
                case "exit":
                    node.disconnect();
                    System.exit(0);
                default:
                    System.out.println("Unknown command. Type 'help'.");
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
            help                         Show commands
            connect <ip> <port>          Connect to Peer
            peers                        List connected peers
            chain                        Print blockchain
            mempool                      Show pending transactions
            send <pubKey> <amount>       Create + broadcast transaction
            mine                         Mine a block from mempool
            exit                         Quit
        """);
    }
}