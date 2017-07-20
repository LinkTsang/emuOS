package emuos.os;

/**
 * Created by Link on 2017/7/20.
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
