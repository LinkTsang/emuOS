package emuos.os.shell;

/**
 * @author Link
 */
public interface Handler {
    public static final Handler NULL = () -> {
    };

    void handle();
}
