/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.generator.Generator;
import emuos.generator.TinyLexer;
import emuos.generator.TinyParser;
import emuos.generator.Token;
import org.junit.*;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * @author Link
 */
public class CompilerTest {

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
        String code = "x=123\n"
                + "x++\n"
                + "x--\n"
                + "!A6\n"
                + "end";
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
        String code = "x=123\n"
                + "x++\n"
                + "x--\n"
                + "!A6\n"
                + "end";
        TinyLexer lexer = new TinyLexer(code);
        Generator gen = new Generator();
        TinyParser parser = new TinyParser(lexer, gen);
        parser.program();
    }

    @Test
    public void testGenerator() throws IOException {
        String code = "x=123\n"
                + "x++\n"
                + "x--\n"
                + "!A6\n"
                + "end";
        TinyLexer lexer = new TinyLexer(code);
        Generator gen = new Generator();
        TinyParser parser = new TinyParser(lexer, gen);
        parser.program();
        byte[] byteCode = gen.getCode();
        System.out.println(Arrays.toString(byteCode));
        gen.dumpToFile("test.e");
    }
}
