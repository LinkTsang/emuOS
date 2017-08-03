package emuos.os;

/**
 * @author Link
 */
public class Shell {
    OutputListener outputListener = null;

    public Shell(OutputListener listener) {
        outputListener = listener;
    }

    public void write(String input) {

    }

    private void output(String message) {
        if (outputListener != null) {
            outputListener.on(message);
        }
    }

    public interface OutputListener {
        void on(String text);
    }
}
