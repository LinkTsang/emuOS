package emuos.os.shell;

/**
 * @author Link
 */
public interface Handler {
    public static final Handler NULL = new Handler() {
        @Override
        public void handle() {
        }
    };
    void handle();
}
