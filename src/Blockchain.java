/*
 * Gavin MacFadyen
 *
 * Enforces consensus rules, tracks blocks, and maintains the UTXO state.
 * All validation happens here; no block or transaction can change state
 * without passing through this class.
*/
import java.io.*;
import java.security.PublicKey;
import java.util.*;

public class Blockchain implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final ArrayList<Block> chain;
    private final int difficulty = 5; //Number of leading 0s

    private final Map<String, TransactionOutput> UTXO = new HashMap<>();

    public Blockchain () {
        chain = new ArrayList<>();
        chain.add(createGenesisBlock());
        rebuildUTXO();
    }

    private String target () {
        return "0".repeat(difficulty);
    }

    //This is the first block in a chain, it must be created uniquely and deterministicly
    //so all nodes have the same genesis in their chain.
    private Block createGenesisBlock() {
        Block genesis = new Block(0, "0");

        genesis.timestamp = 0;
        genesis.nonce = 0;
        genesis.transactions.clear();

        // Deterministic output ID
        TransactionOutput out = new TransactionOutput(
                "GENESIS_UTXO",
                GenesisUtil.GENESIS_PUBLIC_KEY,
                0
        );

        Transaction coinbase = new Transaction(
                GenesisUtil.GENESIS_PUBLIC_KEY,
                List.of(),
                List.of(out)
        );

        // Deterministic signature
        coinbase.signature = new byte[0];

        // Deterministic txId
        coinbase.txId = "GENESIS_TX";

        genesis.transactions.add(coinbase);

        genesis.hash = genesis.computeHash();
        return genesis;
    }


    //Save/Load to and from disk
    public synchronized void saveToDisk (String filename) {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
            out.flush();
            //System.out.println("Saved Blockchain to Disk"); Annoying print, saved for debugging.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Blockchain loadFromDisk(String filename) {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            Blockchain chain = (Blockchain) in.readObject();
            chain.rebuildUTXO();
            //System.out.println("Blockchain loaded from disk"); Annoying print, saved for debugging.

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

    //Checks whether a new block is valid and can be added to the chain.
    //If anything is wrong (wrong parent, bad hash, invalid transactions),
    //the block is rejected and the chain is left unchanged.
    public synchronized boolean tryAddBlock(Block block) throws Exception {
        Block last = getLatestBlock();

        if (!block.prevHash.equals(last.hash)) {
            System.out.println("[REJECT] prevHash mismatch");
            return false;
        }

        if (block.timestamp < last.timestamp) {
            System.out.println("[REJECT] timestamp invalid");
            return false;
        }

        if (!block.computeHash().equals(block.hash)) {
            System.out.println("[REJECT] hash mismatch");
            System.out.println("[REJECT] computed = " + block.computeHash());
            System.out.println("[REJECT] stored   = " + block.hash);
            return false;
        }

        if (!block.hash.startsWith(target())) {
            System.out.println("[REJECT] PoW invalid");
            return false;
        }

        for (Transaction tx : block.transactions) {
            if (!validateTransaction(tx)) {
                System.out.println("[REJECT] invalid transaction");
                return false;
            }
        }

        chain.add(block);
        for (Transaction tx : block.transactions) {
            applyTransaction(tx);
        }

        return true;
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
            if (curr.timestamp < prev.timestamp) return false;
            if (!curr.hash.equals(curr.computeHash())) return false;
            if (!curr.hash.startsWith(target())) return false;
        }
        return true;
    }

    //Checks whether a transaction is allowed to happen according to the current blockchain state.
    //For coinbase transactions, this only allows minting the fixed block reward.
    //For normal transactions, this verifies the signature, checks that all referenced UTXOs exist,
    //belong to the sender, and that the total input value is >= total output value.
    //This prevents fake coins, double-spending, and unauthorized spending.
    public boolean validateTransaction(Transaction tx) throws Exception {
        //Coinbase transaction
        if (tx.inputs.isEmpty()) {
            return tx.outputs.size() == 1 && tx.outputs.get(0).amount == 1;
        }

        //Normal transaction
        if (!tx.verify()) return false;

        long inputSum = 0;

        for (TransactionInput in : tx.inputs) {
            TransactionOutput utxo = UTXO.get(in.outputId);
            if (utxo == null) return false;
            if (!utxo.recipient.equals(tx.sender)) return false;
            inputSum += utxo.amount;
        }

        long outputSum = tx.outputs.stream().mapToLong(o -> o.amount).sum();

        return inputSum >= outputSum;
    }

    //Applies a valid transaction to the blockchain state.
    //All input UTXOs are removed (spent), and all output UTXOs are added (created).
    //This is the only place where the UTXO set is mutated, and it is only called
    //after a transaction has already been fully validated.
    private void applyTransaction (Transaction tx) {
        for (TransactionInput in : tx.inputs) {
            UTXO.remove(in.outputId);
        }

        for (TransactionOutput out : tx.outputs) {
            UTXO.put(out.id, out);
        }
    }

    //Rebuilds the UTXO set from scratch so it matches the current chain exactly.
    private void rebuildUTXO() {
        UTXO.clear();

        for (Block block : chain) {
            for (Transaction tx : block.transactions) {
                for (TransactionInput in : tx.inputs) {
                    UTXO.remove(in.outputId);
                }
                for (TransactionOutput out : tx.outputs) {
                    UTXO.put(out.id, out);
                }
            }
        }
    }

    //This is our "Most up-to-date chain" check, it is based on whichever chain is longer.
    public synchronized boolean maybeReplaceChain (ArrayList<Block> newChain) {
        if (newChain.size() <= chain.size()) return false;
        if (!isValidChain(newChain)) return false;

        chain.clear();
        chain.addAll(newChain);
        rebuildUTXO();
        return true;
    }

    //These methods are basic "Getters" and are useful for the CLI.
    public ArrayList<Block> getChain () {
        return chain;
    }

    public Map<String, TransactionOutput> getUTXO () {
        return Collections.unmodifiableMap(UTXO);
    }

    public long getBalance(PublicKey key) {
        long balance = 0;

        for (TransactionOutput out : UTXO.values()) {
            if (out.recipient.equals(key)) {
                balance += out.amount;
            }
        }

        return balance;
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

    public int length () {
        return chain.size();
    }

    public void printUTXO() {
        System.out.println("UTXO set:");
        for (TransactionOutput out : UTXO.values()) {
            System.out.println(Base64.getEncoder().encodeToString(out.recipient.getEncoded()) + " -> " + out.amount
            );
        }
    }

    public void printChain() {
        for (Block block : chain) {
            System.out.println(
                "Block " + block.index +
                " | txs=" + block.transactions.size() +
                " | hash=" + block.hash.substring(0, 10)
            );
        }
    }
}