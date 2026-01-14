/*
 * Gavin MacFadyen
 *
 * These are the individual blocks in the blockchain. Their data is hashed to become unique and "Matchable".
*/
import java.io.Serializable;
import java.util.ArrayList;

public class Block implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int index;
    public long timestamp;
    public ArrayList<Transaction> transactions;

    public int nonce;
    public final String prevHash;
    public String hash;

    public Block (int index, String prevHash) {
        this.index = index;
        this.prevHash = prevHash;
        this.timestamp = System.currentTimeMillis();
        this.transactions = new ArrayList<>();
        this.nonce = 0;
        this.hash = computeHash();
    }

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    public String computeHash() {
        StringBuilder txData = new StringBuilder();

        for (Transaction tx : transactions) {
            txData.append(tx.txId);
        }

        return HashUtil.sha256(index + prevHash + timestamp + txData + nonce);
    }
}
