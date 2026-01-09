public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        Node nodeB = new Node(5001);
        nodeB.start();

        Thread.sleep(1000);
        nodeB.syncWithPeer("localhost", 54000);

        nodeB.getBlockchain().printChain();
    }   
}
