/*
 * Gavin MacFadyen
 *
 * This is the blockchain data structure that each node stores a copy of. It is a list of blocks.
*/
import java.io.*;
import java.io.Serializable;
import java.util.ArrayList;

public class Blockchain implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ArrayList<Block> chain;
    private final int difficulty = 4; //Number of leading 0s

    public Blockchain () {
        chain = new ArrayList<>();
        chain.add(createGenesisBlock());
    }

    private String target () {
        return "0".repeat(difficulty);
    }

    //This is the first block in a chain, it must be created uniquely.
    private Block createGenesisBlock () {
        Block genesis = new Block(0, "0");

        genesis.timestamp = 0;
        genesis.nonce = 0;

        genesis.addTransaction(new Transaction("SYSTEM", "Gavin", 10));
        genesis.hash = genesis.computeHash();

        return genesis;
    }

    //Save/Load to and from disk
    public synchronized void saveToDisk (String filename) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
            out.flush();

            System.out.println("Saved Blockchain to Disk");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Blockchain loadFromDisk(String filename) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            Blockchain chain = (Blockchain) in.readObject();
            System.out.println("Blockchain loaded from disk");
            return chain;
        } catch (FileNotFoundException e) {
            //File does not exist yet
            System.out.println("No existing blockchain file, creating new chain");
            return new Blockchain();
        } catch (Exception e) {
            //Corrupt file or incompatible version
            System.out.println("Failed to load blockchain, creating new chain");
            e.printStackTrace();
            return new Blockchain();
        }
    }


    public Block getLatestBlock () {
        return chain.get(length() - 1); //getLast doesn't seem to work here?
    }

    public boolean containsBlock (String hash) {
        for (Block block : chain) {
            if (block.hash.equals(hash)) return true;
        }

        return false;
    }

    //This is for adding the node locally.
    public void addBlock (Block block) {
        mineBlock(block);
        chain.add(block);
    }

    //We can only add a block if its prevHash matches the current last blocks hash. If there are leading x zeros.
    public synchronized boolean tryAddBlock (Block block) {
        Block last = getLatestBlock();

        if (!block.prevHash.equals(last.hash)) return false;
        if (!block.computeHash().equals(block.hash)) return false;
        if (!block.hash.startsWith(target())) return false;

        chain.add(block);
        return true;
    }

    public int length () {
        return chain.size();
    }

    //Mine the block by updating the nonce and recomputing the hash until it contains x leading zeros.
    private void mineBlock (Block block) {
        while (!block.hash.startsWith(target())) {
            block.nonce++;
            block.hash = block.computeHash();
        }
        System.out.println("Mined Block " + block.index + " : " + block.hash);
    }

    //Checks if all blocks are valid in a chain.
    public boolean isValidChain (ArrayList<Block> otherChain) {
        if (otherChain.size() == 0) return false;

        // Genesis blocks must match.
        if (!otherChain.get(0).hash.equals(chain.get(0).hash)) return false;

        for (int i = 1; i < otherChain.size(); i++) {
            Block curr = otherChain.get(i);
            Block prev = otherChain.get(i - 1);

            if (!curr.prevHash.equals(prev.hash)) return false;
            if (!curr.hash.equals(curr.computeHash())) return false;
            if (!curr.hash.startsWith(target())) return false;
        }
        return true;
    }

    //This is our "Most up to date chain" check, it is based on whichever chain is longer.
    public synchronized boolean maybeReplaceChain (ArrayList<Block> newChain) {
        if (newChain.size() <= chain.size()) return false;
        if (!isValidChain(newChain)) return false;

        chain.clear();
        chain.addAll(newChain);
        return true;
    }

    public ArrayList<Block> getChain () {
        return chain;
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