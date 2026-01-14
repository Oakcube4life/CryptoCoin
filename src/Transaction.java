/*
 * Gavin MacFadyen
 *
 * Basic transaction storage here. Most of this is GPT generated, I don't entirely understand the signing and verifying of transactions.
 */
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public final String sender;
    public final String reciever;
    public final int amount;
    public final long timestamp;
    public final String txId;
    public byte[] signature;

    public Transaction (String sender, String reciever, int amount) {
        this.sender = sender;
        this.reciever = reciever;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
        this.txId = computeHash();
    }

    private String computeHash () {
        return HashUtil.sha256(sender + reciever + amount + timestamp);
    }

    public void sign (PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(txId.getBytes(StandardCharsets.UTF_8));
        this.signature = sig.sign();
    }

    public boolean verify () throws Exception {
        if (sender.equals("COINBASE")) return true;

        byte[] keyBytes = Base64.getDecoder().decode(sender);
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(key);
        sig.update(txId.getBytes(StandardCharsets.UTF_8));
        return sig.verify(signature);
    }

    @Override
    public String toString () {
        return sender + " -> " + reciever + " : " + amount;
    }

    //These are useful to prevent duplicate transactions across gossip (GPT helped here lol)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;
        return txId.equals(((Transaction) o).txId);
    }

    @Override
    public int hashCode() {
        return txId.hashCode();
    }
}
