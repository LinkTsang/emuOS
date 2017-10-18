package emuos.compiler;

/**
 * @author Link
 */
public class UnexpectedTokenException extends ParseException {

    private final Token token;

    public UnexpectedTokenException(int line, int column, Token token) {
        super(line, column, "Unexpected token: " + token);
        this.token = token;
    }

    public Token getToken() {
        return token;
    }
}
