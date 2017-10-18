package emuos.compiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Link
 */
public class TinyCompiler {
    byte[] byteCode = new byte[0];

    public TinyCompiler() {
    }

    public void compile(String code) throws UnexpectedTokenException, TokenMismatchException, GeneratorException {
        TinyLexer lexer = new TinyLexer(code);
        Generator gen = new Generator();
        TinyParser parser = new TinyParser(lexer, gen);
        parser.program();
        byteCode = gen.getCode();
    }

    public byte[] getByteCode() {
        return byteCode;
    }

    public void dumpToFile(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(byteCode, 0, byteCode.length);
        }
    }

    public void dumpToFile(String path) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(path)) {
            fos.write(byteCode, 0, byteCode.length);
        }
    }
}
