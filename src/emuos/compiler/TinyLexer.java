/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.compiler;

/**
 * @author Link
 */
public class TinyLexer extends Lexer {

    private int lineCount = 1;

    public TinyLexer(String input) {
        super(input);
    }

    public void WS() {
        while (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            if (c == '\n') {
                ++lineCount;
            }
            consume();
        }
    }

    public Token IO() {
        consume();
        return new Token(Token.Type.IO_COMMAND, "!");
    }

    public Token ID() {
        StringBuilder buffer = new StringBuilder();
        do {
            buffer.append((char) c);
            consume();
        } while (Character.isLetter(c));
        return new Token(Token.Type.ID, buffer.toString());
    }

    public Token INCREASE() {
        consume();
        match('+');
        return new Token(Token.Type.INCREASE, "++");
    }

    public Token DECREASE() {
        consume();
        match('-');
        return new Token(Token.Type.DECREASE, "--");
    }

    public Token INT() {
        StringBuilder buffer = new StringBuilder();
        do {
            buffer.append((char) c);
            consume();
        } while (Character.isDigit(c));
        return new Token(Token.Type.INT, buffer.toString());
    }

    public Token nextToken() {
        while (c != EOF) {
            switch (c) {
                case ' ':
                case '\t':
                case '\n':
                case '\r':
                    WS();
                    continue;
                case '!':
                    return IO();
                case '=':
                    consume();
                    return new Token(Token.Type.EQUALS, "=");
                case '+':
                    return INCREASE();
                case '-':
                    return DECREASE();
                default:
                    if (Character.isLetter(c)) {
                        if (input.substring(p).startsWith("end")) {
                            consume();
                            consume();
                            consume();
                            return new Token(Token.Type.END, "<END>");
                        }
                        return ID();
                    } else if (Character.isDigit(c)) {
                        return INT();
                    }
                    throw new Error("invalid character: " + (char) c);
            }
        }
        return new Token(Token.Type.EOF, "<EOF>");
    }

    public int getLineCount() {
        return lineCount;
    }
}
