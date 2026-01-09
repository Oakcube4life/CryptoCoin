public class Main {
    public static void main(String[] args) {
        Blockchain coin = new Blockchain();

        Block b1 = new Block(1, coin.getLatestBlock().hash);
        b1.addTransaction(new Transaction("Oakcube4Life", "Spaceman", 50));
        b1.addTransaction(new Transaction("Oakcube4life", "OptimusJ", 25));
        coin.addBlock(b1);

        Block b2 = new Block(2, coin.getLatestBlock().hash);
        b2.addTransaction(new Transaction("Spaceman", "OptimusJ", 10));
        coin.addBlock(b2);

        coin.printChain();
    }
}
