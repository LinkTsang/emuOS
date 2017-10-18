package emuos.compiler;

/**
 * @author Link
 */
public abstract class Lexer {
    public static final int EOF = -1;
    protected final String input;
    protected int p;
    protected int c;
    protected int line = 1;
    protected int column = 1;
    protected int currentBeginLine = 1;
    protected int currentBeginColumn = 1;

    public Lexer(String input) {
        this.input = input;
        c = input.charAt(p);
    }

    public void consume() {
        p++;
        if (c != '\n') {
            column++;
        } else {
            line++;
            column = 1;
        }
        if (p >= input.length()) {
            c = EOF;
        } else {
            c = input.charAt(p);
        }
    }

    public boolean match(char x) {
        if (c == x) {
            consume();
            return true;
        } else {
            return false;
        }
    }

    public abstract Token nextToken() throws ParseException;

    public int getCurrentBeginLine() {
        return currentBeginLine;
    }

    public int getCurrentBeginColumn() {
        return currentBeginColumn;
    }
}
