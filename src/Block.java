import java.security.MessageDigest;
import java.util.ArrayList;

public class Block {
    public final int index;
    public final long timestamp;
    public final ArrayList<Transaction> transactions;

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

    public String computeHash () {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = index + prevHash + timestamp + transactions.toString() + nonce;

            byte[] hashBytes = digest.digest(data.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();

            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
