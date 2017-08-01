/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.compiler;

/**
 * @author Link
 */
public class Instruction {

    public static final byte OPCODE_ASSIGNMENT = 1;
    public static final byte OPCODE_DECREASE = 3;
    public static final byte OPCODE_END = 0;
    public static final byte OPCODE_INCREASE = 2;
    public static final byte OPCODE_IO = 4;
    protected Type type;

    public enum Type {
        ASSIGNMENT,
        INCREASE,
        DECREASE,
        IOCOMMAND,
        END
    }

    public class Assignment extends Instruction {
        private final int value;

        public Assignment(int value) {
            this.type = Type.ASSIGNMENT;
            this.value = value;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }
}
