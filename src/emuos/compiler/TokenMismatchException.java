package emuos.compiler;

/**
 * @author Link
 */
public class TokenMismatchException extends ParseException {
    private final Token.Type excepted;
    private final Token actual;

    public TokenMismatchException(int line, int column, Token.Type excepted, Token actual) {
        super(line, column, "excepting <" + excepted + ">; found " + actual);
        this.excepted = excepted;
        this.actual = actual;
    }

    public Token.Type getExcepted() {
        return excepted;
    }

    public Token getActual() {
        return actual;
    }
}
