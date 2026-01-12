import java.util.Scanner;

public class Main1 {
    public static void main(String[] args) {
        Node nodeA = new Node(54000);
        nodeA.start();

        // Node A knows about Node B
        nodeA.addPeer("localhost", 54001);

        System.out.println("Node A ready");

        Scanner s = new Scanner(System.in);

        //Broadcast on enter
        s.nextLine();

        Block b = new Block(
                nodeA.getBlockchain().length(),
                nodeA.getBlockchain().getLatestBlock().hash
        );
        b.addTransaction(new Transaction("Gavin", "Alice", 10));
        nodeA.mineAndBroadcast(b);
    }
}
