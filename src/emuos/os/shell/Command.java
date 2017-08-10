package emuos.os.shell;

/**
 * @author Link
 */
public abstract class Command {
    private final String[] names;

    public Command(String... names) {
        this.names = names;
    }

    public String[] names() {
        return names;
    }

    public abstract void execute(String args);
}
