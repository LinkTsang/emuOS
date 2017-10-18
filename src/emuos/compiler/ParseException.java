package emuos.compiler;

/**
 * @author Link
 */
public class ParseException extends Exception {
    private int line;
    private int column;

    public ParseException(int line, int column, String message) {
        super("(" + line + ":" + column + "): " + message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
