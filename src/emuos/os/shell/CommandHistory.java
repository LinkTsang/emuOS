package emuos.os.shell;

import java.util.LinkedList;
import java.util.ListIterator;

/**
 * @author Link
 */
public class CommandHistory {
    private static final int MAX_HISTORY_COUNT = 50;
    private final LinkedList<String> historyList = new LinkedList<>();
    private ListIterator<String> current = historyList.listIterator();

    public void add(String command) {
        if (!historyList.isEmpty() && historyList.getFirst().equals(command)) {
            return;
        }
        historyList.addFirst(command);
        if (historyList.size() > MAX_HISTORY_COUNT) {
            historyList.removeLast();
        }
        reset();
    }

    public void reset() {
        current = historyList.listIterator();
    }

    public String prev() {
        if (current.hasNext()) {
            return current.next();
        }
        if (current.hasPrevious()) {
            current.previous();
            return current.next();
        }
        return "";

    }

    public String next() {
        if (current.hasPrevious()) {
            return current.previous();
        } else {
            return "";
        }
    }
}
