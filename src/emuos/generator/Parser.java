/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.generator;

/**
 * @author Link
 *         <p>
 *         <code>
 *         program : expr* | END ;
 *         expr : ID '=' INT | ID '++' | ID '--' | '!' ID INT ;
 *         ID : [a-zA-Z]+; INT : \d+;
 *         </code>
 */
public class Parser {

    protected final int k;
    protected TinyLexer input;
    protected Token[] lookahead;
    protected int p = 0;

    public Parser(TinyLexer input, int k) {
        this.input = input;
        this.k = k;
        lookahead = new Token[k];
        for (int i = 0; i < k; ++i) {
            consume();
        }
    }

    protected final Token match(Token.Type x) {
        Token currentToken = LT(1);
        if (LA(1) == x) {
            consume();
        } else {
            throw new Error("excepting " + x + "; found " + lookahead[p].toString());
        }
        return currentToken;
    }

    protected final void consume() {
        lookahead[p] = input.nextToken();
        p = (p + 1) % k;
    }

    public Token LT(int i) {
        return lookahead[(p + i - 1) % k];
    }

    public Token.Type LA(int i) {
        return LT(i).type;
    }
}
