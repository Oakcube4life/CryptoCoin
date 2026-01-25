import javax.swing.*;
import java.io.OutputStream;

/**
 * @author Brandon Kuciapski
 * Redirects System.out / System.err into a JTextArea
 */
public class TextAreaOutputStream extends OutputStream {

    private final JTextArea textArea;

    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) {
        SwingUtilities.invokeLater(() ->
                textArea.append(String.valueOf((char) b))
        );
    }
}
