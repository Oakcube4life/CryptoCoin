/*
 * Gavin MacFadyen
 *
 * Creates a new spendable value.
 */
import java.io.Serializable;
import java.security.PublicKey;
import java.util.UUID;

public class TransactionOutput implements Serializable {
    public final String id;
    public final PublicKey recipient;
    public final long amount;

    //Normal outputs (random)
    public TransactionOutput(PublicKey recipient, long amount) {
        this(UUID.randomUUID().toString(), recipient, amount);
    }

    //Deterministic output (genesis)
    public TransactionOutput(String id, PublicKey recipient, long amount) {
        this.id = id;
        this.recipient = recipient;
        this.amount = amount;
    }
}