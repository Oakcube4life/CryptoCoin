/*
 * Gavin MacFadyen
 *
 * This references an existing unspent output.
 */
import java.io.Serializable;

public class TransactionInput implements Serializable {
    public final String outputId;

    public TransactionInput(String outputId) {
        this.outputId = outputId;
    }
}
