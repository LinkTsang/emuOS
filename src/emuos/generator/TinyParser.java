/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.generator;

import emuos.generator.Token.Type;

/**
 * @author Link
 */
public class TinyParser extends Parser {

    Generator generator;

    public TinyParser(TinyLexer lexer, Generator generator) {
        super(lexer, 3);
        this.generator = generator;
    }

    public void program() throws Error {
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
                            throw new Error("Line: " + input.getLineCount() + ": invalid token: " + LT(2).toString());
                    }
                    break;
                case IO_COMMAND:
                    io_command();
                    break;
                case END:
                    end();
                    return;
            }
        }
        throw new Error("excepting 'end'; found" + LA(1));
    }

    protected void assignment() {
        match(Type.ID);
        match(Type.EQUALS);
        Token valueToken = match(Type.INT);
        generator.assign(Byte.valueOf(valueToken.text));
    }

    protected void increase() {
        match(Type.ID);
        match(Type.INCREASE);
        generator.increase();
    }

    protected void decrease() {
        match(Type.ID);
        match(Type.DECREASE);
        generator.decrease();
    }

    protected void io_command() {
        match(Type.IO_COMMAND);
        Token idToken = match(Type.ID);
        Token timeToken = match(Type.INT);
        generator.io(idToken.text, Byte.valueOf(timeToken.text));
    }

    protected void end() {
        match(Type.END);
        generator.end();
    }

}
