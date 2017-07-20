/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.generator;

/**
 * @author Link
 */
public class Token {

    public Type type;
    public String text;

    public Token(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    @Override
    public String toString() {
        String tname = type.name();
        return "<'" + text + "'," + tname + ">";
    }

    public enum Type {
        NONE,
        EOF,
        EQUALS,
        INCREASE,
        DECREASE,
        ID,
        INT,
        IO_COMMAND,
        END,
        NUMBER
    }
}
