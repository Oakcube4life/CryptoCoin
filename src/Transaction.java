import java.io.Serializable;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public final String sender;
    public final String reciever;
    public final int amount;

    public Transaction (String sender, String reciever, int amount) {
        this.sender = sender;
        this.reciever = reciever;
        this.amount = amount;
    }

    @Override
    public String toString () {
        return sender + " -> " + reciever + " : " + amount;
    }
}
