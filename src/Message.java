import java.io.Serializable;

public class Message implements Serializable {
    public String type;
    public Object data;

    public Message (String type, Object data) {
        this.type = type;
        this.data = data;
    }
}
