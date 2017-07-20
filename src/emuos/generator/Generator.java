/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static emuos.generator.Instruction.*;

/**
 * @author Link
 */
public class Generator {

    public static final int INITIAL_CODE_SIZE = 1024;
    protected byte[] code = new byte[INITIAL_CODE_SIZE];
    protected int ip = -1;
    protected int size = INITIAL_CODE_SIZE;

    private void write(byte value) {
        if (++ip >= code.length) {
            size *= 1.5;
            byte[] buffer = new byte[size];
            System.arraycopy(code, 0, buffer, 0, code.length);
            code = buffer;
        }
        code[ip] = value;
    }

    public byte[] getCode() {
        return Arrays.copyOf(code, getSize());
    }

    public int getSize() {
        return ip + 1;
    }

    public void dumpToFile(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(code, 0, getSize());
        }
    }

    public void dumpToFile(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(code, 0, getSize());
        }
    }

    public void assign(byte value) {
        write(OPCODE_ASSIGNMENT);
        write(value);
    }

    public void increase() {
        write(OPCODE_INCREASE);
    }

    public void decrease() {
        write(OPCODE_DECREASE);
    }

    public void io(String ID, byte time) {
        byte id;
        switch (ID) {
            case "A":
                id = 0;
                break;
            case "B":
                id = 1;
                break;
            case "C":
                id = 2;
                break;
            default:
                throw new Error(String.format("Unsupport device ID %s.", ID));
        }
        write(OPCODE_IO);
        write(id);
        write(time);
    }

    public void end() {
        write(OPCODE_END);
    }
}
