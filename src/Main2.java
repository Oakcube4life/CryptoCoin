public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        Node nodeB = new Node(54001);
        nodeB.start();

        // Node B knows about Node A
        nodeB.addPeer("localhost", 54000);

        // Give Node A time to start
        Thread.sleep(1000);

        // Initial sync (Phase 1 behavior)
        nodeB.syncWithPeer("localhost", 54000);

        System.out.println("Node B synced and ready");
    }
}
