package emuos.compiler;

/**
 * Created by Link on 2017/7/19.
 */
public abstract class Lexer {
    public static final int EOF = -1;
    protected final String input;
    protected int p;
    protected int c;

    public Lexer(String input) {
        this.input = input;
        c = input.charAt(p);
    }

    public void consume() {
        p++;
        if (p >= input.length()) {
            c = EOF;
        } else {
            c = input.charAt(p);
        }
    }

    public void match(char x) {
        if (c == x) {
            consume();
        } else {
            throw new Error("expecting " + x + "; found " + (char) c);
        }
    }

    public abstract Token nextToken();
}
