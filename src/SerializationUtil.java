/*
 * Gavin MacFadyen
 *
 * Helper for turning objects into a consistent serialized form for hashing.
 */
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class SerializationUtil {
    public static byte[] serialize(Object... objects) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);

            for (Object obj : objects) {
                out.writeObject(obj);
            }

            out.flush();
            return bos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

