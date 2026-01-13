/*
 * Gavin MacFadyen
 *
 * Simple interface to host and connect to simulate the blockchain.
 * This was made with one GPT prompt lol.
*/
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Simple Blockchain Node ===");
        System.out.print("Enter port to run node on: ");
        int myPort = Integer.parseInt(scanner.nextLine());

        Node node = new Node(myPort);
        node.start();

        System.out.print("Host new network or connect to peer? (host/connect): ");
        String mode = scanner.nextLine().trim().toLowerCase();

        if (mode.equals("connect")) {
            System.out.print("Enter peer host (e.g. localhost): ");
            String peerHost = scanner.nextLine();

            System.out.print("Enter peer port: ");
            int peerPort = Integer.parseInt(scanner.nextLine());

            node.addPeer(peerHost, peerPort);

            //Give peer time to be ready
            Thread.sleep(1000);
            node.syncWithPeer(peerHost, peerPort);
        }

        //Command loop
        while (true) {
            System.out.println("\nCommands:");
            System.out.println("  mine  - mine a new block");
            System.out.println("  chain - print blockchain");
            System.out.println("  exit  - quit");

            String cmd = scanner.nextLine().trim().toLowerCase();

            switch (cmd) {
                case "mine":
                    Block block = new Block(
                        node.getBlockchain().length(),
                        node.getBlockchain().getLatestBlock().hash
                    );

                    //Simple demo transaction
                    block.addTransaction(
                        new Transaction("Gavin", "Friend", 10)
                    );

                    node.mineAndBroadcast(block);
                    System.out.println("Block mined and broadcasted");
                    break;

                case "chain":
                    node.getBlockchain().printChain();
                    break;

                case "exit":
                    System.out.println("Shutting down node...");
                    System.exit(0);
                    return;

                default:
                    System.out.println("Unknown command");
            }
        }
    }
}