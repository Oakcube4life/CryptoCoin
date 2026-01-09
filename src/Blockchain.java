import java.util.ArrayList;

public class Blockchain {
    private final ArrayList<Block> chain;
    private final int difficulty = 0; //Number of leading 0s

    public Blockchain () {
        chain = new ArrayList<>();
        chain.add(createGenesisBlock());
    }

    private Block createGenesisBlock () {
        Block genesis = new Block(0, "0");
        genesis.addTransaction(new Transaction("SYSTEM", "Gavin", 10));
        mineBlock(genesis);
        return genesis;
    }

    public Block getLatestBlock () {
        return chain.get(chain.size() - 1);
    }

    public void addBlock (Block b) {
        b.hash = b.computeHash();
        mineBlock(b);
        chain.add(b);
    }

    private void mineBlock (Block b) {
        String target = "0".repeat(difficulty);
        while (!b.hash.startsWith(target)) {
            b.nonce++;
            b.hash = b.computeHash();
        }
        System.out.println("Mined Block " + b.index + " : " + b.hash);
    }

    public void printChain () {
        for (Block b : chain) {
            System.out.println("---- BLOCK " + b.index + " ----");
            System.out.println("Hash: " + b.hash);
            System.out.println("Prev: " + b.prevHash);
            System.out.println("Transactions: ");
            for (Transaction t : b.transactions) {
                System.out.println(" " + t);
            }
        }
    } 
}