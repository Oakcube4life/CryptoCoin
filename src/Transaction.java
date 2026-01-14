/*
 * Gavin MacFadyen
 *
 * Basic transaction storage here. Most of this is GPT generated, I don't entirely understand the signing and verifying of transactions.
 */
import java.io.Serializable;
import java.util.List;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public final PublicKey sender;
    public final List<TransactionInput> inputs;
    public final List<TransactionOutput> outputs;

    public final long timestamp;
    public String txId;
    public byte[] signature;

    public Transaction (PublicKey sender, List<TransactionInput> inputs, List<TransactionOutput> outputs) {
        this.sender = sender;
        this.inputs = inputs;
        this.outputs = outputs;
        this.timestamp = System.currentTimeMillis();
        this.txId = computeHash();
    }

    public byte[] getDataToSign() {
        return SerializationUtil.serialize(sender, inputs, outputs, timestamp);
    }

    private String computeHash () {
        return HashUtil.sha256(getDataToSign());
    }

    public void sign(PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(getDataToSign());
        this.signature = sig.sign();
    }

    public boolean verify() throws Exception {
        if (inputs.isEmpty()) return true; //Coinbase

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(sender);
        sig.update(getDataToSign());
        return sig.verify(signature);
    }


    @Override
    public String toString() {
        return "Transaction " + txId + " (" + outputs.size() + " outputs)";
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