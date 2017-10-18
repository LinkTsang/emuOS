/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.compiler;

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
    protected int currentBeginLines[];
    protected int currentBeginColumns[];
    protected int p = 0;

    protected Parser(TinyLexer input, int k) {
        this.input = input;
        this.k = k;
        lookahead = new Token[k];
        currentBeginLines = new int[k];
        currentBeginColumns = new int[k];
        for (int i = 0; i < k; ++i) {
            consume();
        }
    }

    protected final Token match(Token.Type x) throws TokenMismatchException {
        Token currentToken = LT(1);
        if (LA(1) == x) {
            consume();
        } else {
            throw new TokenMismatchException(getCurrentBeginLine(), getCurrentBeginLine(), x, lookahead[p]);
        }
        return currentToken;
    }

    protected final void consume() {
        lookahead[p] = input.nextToken();
        currentBeginLines[p] = input.getCurrentBeginLine();
        currentBeginColumns[p] = input.getCurrentBeginColumn();
        p = (p + 1) % k;
    }

    protected Token LT(int i) {
        return lookahead[(p + i - 1) % k];
    }

    protected Token.Type LA(int i) {
        return LT(i).type;
    }

    protected int getCurrentBeginLine() {
        return currentBeginLines[p];
    }

    protected int getCurrentBeginColumn() {
        return currentBeginColumns[p];
    }
}
