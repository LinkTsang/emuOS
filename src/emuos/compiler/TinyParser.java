/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.compiler;

import emuos.compiler.Token.Type;

/**
 * @author Link
 */
public class TinyParser extends Parser {

    Generator generator;

    public TinyParser(TinyLexer lexer, Generator generator) {
        super(lexer, 3);
        this.generator = generator;
    }

    public void program() throws UnexpectedTokenException, GeneratorException, TokenMismatchException {
        while (LA(1) != Type.EOF) {
            switch (LA(1)) {
                case ID:
                    switch (LA(2)) {
                        case EQUALS:
                            assignment();
                            break;
                        case INCREASE:
                            increase();
                            break;
                        case DECREASE:
                            decrease();
                            break;
                        default:
                            consume();
                            throw new UnexpectedTokenException(getCurrentBeginLine(), getCurrentBeginColumn(), LT(1));
                    }
                    break;
                case IO_COMMAND:
                    io_command();
                    break;
                case END:
                    end();
                    return;
                default:
                    throw new UnexpectedTokenException(getCurrentBeginLine(), getCurrentBeginColumn(), LT(1));
            }
        }
        throw new TokenMismatchException(getCurrentBeginLine(), getCurrentBeginColumn(), Type.END, LT(1));
    }

    private void assignment() throws TokenMismatchException {
        match(Type.ID);
        match(Type.EQUALS);
        Token valueToken = match(Type.INT);
        generator.assign(Byte.valueOf(valueToken.text));
    }

    private void increase() throws TokenMismatchException {
        match(Type.ID);
        match(Type.INCREASE);
        generator.increase();
    }

    private void decrease() throws TokenMismatchException {
        match(Type.ID);
        match(Type.DECREASE);
        generator.decrease();
    }

    private void io_command() throws TokenMismatchException, GeneratorException {
        match(Type.IO_COMMAND);
        Token idToken = match(Type.ID);
        Token timeToken = match(Type.INT);
        generator.io(idToken.text, Byte.valueOf(timeToken.text));
    }

    private void end() throws TokenMismatchException {
        match(Type.END);
        generator.end();
    }

}
