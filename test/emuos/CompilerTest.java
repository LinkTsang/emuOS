/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.compiler.*;
import org.junit.*;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Link
 */
public class CompilerTest {

    private static final String code = "x=123\n"
            + "x++\n"
            + "x--\n"
            + "!A6\n"
            + "end";

    public CompilerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testLexer() {
        TinyLexer lexer = new TinyLexer(code);
        Token token;
        while ((token = lexer.nextToken()).type != Token.Type.EOF) {
            System.out.println(token);
        }
        System.out.println(token);

        lexer = new TinyLexer(code);
        assertEquals("<'x',ID>", lexer.nextToken().toString());
        assertEquals("<'=',EQUALS>", lexer.nextToken().toString());
        assertEquals("<'123',INT>", lexer.nextToken().toString());
        assertEquals("<'x',ID>", lexer.nextToken().toString());
        assertEquals("<'++',INCREASE>", lexer.nextToken().toString());
        assertEquals("<'x',ID>", lexer.nextToken().toString());
        assertEquals("<'--',DECREASE>", lexer.nextToken().toString());
        assertEquals("<'!',IO_COMMAND>", lexer.nextToken().toString());
        assertEquals("<'A',ID>", lexer.nextToken().toString());
        assertEquals("<'6',INT>", lexer.nextToken().toString());
        assertEquals("<'<END>',END>", lexer.nextToken().toString());
        assertEquals("<'<EOF>',EOF>", lexer.nextToken().toString());
    }

    @Test
    public void testParser() {
        TinyLexer lexer = new TinyLexer(code);
        Generator gen = new Generator();
        TinyParser parser = new TinyParser(lexer, gen);
        parser.program();
    }

    @Test
    public void testCompiler() {
        TinyCompiler compiler = new TinyCompiler();
        compiler.compile(code);
        byte[] byteCode = compiler.getByteCode();
        System.out.println(Arrays.toString(byteCode));
    }
}
