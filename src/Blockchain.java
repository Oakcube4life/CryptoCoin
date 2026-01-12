import java.io.Serializable;
import java.util.ArrayList;

public class Blockchain implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ArrayList<Block> chain;
    private final int difficulty = 0; //Number of leading 0s

    public Blockchain () {
        chain = new ArrayList<>();
        chain.add(createGenesisBlock());
    }

    private Block createGenesisBlock () {
        Block genesis = new Block(0, "0");

        genesis.timestamp = 0;
        genesis.nonce = 0;

        genesis.addTransaction(new Transaction("SYSTEM", "Gavin", 10));
        genesis.hash = genesis.computeHash();

        return genesis;
    }

    public Block getLatestBlock () {
        return chain.getLast();
    }

    public void addBlock (Block block) {
        block.hash = block.computeHash();
        mineBlock(block);
        chain.add(block);
    }

    public boolean containsBlock (String hash) {
        for (Block block : chain) {
            if (block.hash.equals(hash)) return true;
        }

        return false;
    }

    public synchronized boolean tryAddBlock (Block block) {
        Block last = getLatestBlock();

        if (!block.prevHash.equals(last.hash)) return false;
        if (!block.computeHash().equals(block.hash)) return false;
        if (!block.hash.startsWith("0".repeat(difficulty))) return false;

        chain.add(block);
        return true;
    }

    public int length () {
        return chain.size();
    }

    public void replaceChain (Blockchain newChain) {
        this.chain.clear();
        this.chain.addAll(newChain.chain);
    }

    private void mineBlock (Block block) {
        String target = "0".repeat(difficulty);
        while (!block.hash.startsWith(target)) {
            block.nonce++;
            block.hash = block.computeHash();
        }
        System.out.println("Mined Block " + block.index + " : " + block.hash);
    }

    public void printChain () {
        for (Block block : chain) {
            System.out.println("---- BLOCK " + block.index + " ----");
            System.out.println("Hash: " + block.hash);
            System.out.println("Prev: " + block.prevHash);
            System.out.println("Transactions: ");
            for (Transaction t : block.transactions) {
                System.out.println(" " + t);
            }
        }
    } 
}