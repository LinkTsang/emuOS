/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.FileSystem;
import emuos.diskmanager.InputStream;
import emuos.diskmanager.OutputStream;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @author Link
 */
public class DiskTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();
    private String theRose = "Some say love, it is a river,\n" +
            "That drowns, the tender reed.\n" +
            "Some say love, it is a razor,\n" +
            "that leaves, your soul to bleed.\n" +
            "Some say love, it is a hunger,\n" +
            "An endless aching need.\n" +
            "I say love, it is a flower,\n" +
            "And you its only seed.\n" +
            "\n" +
            "It's the heart, afraid of breaking,\n" +
            "That never, learns to dance.\n" +
            "It's the dream, afraid of waking,\n" +
            "That never, takes the chance.\n" +
            "It's the one, who won't be taken,\n" +
            "Who cannot, seem to give.\n" +
            "And the soul, afraid of dying,\n" +
            "That never, learns to live.\n" +
            "\n" +
            "When the night, has been too lonely,\n" +
            "And the road, has been too long.\n" +
            "And you feel, that love is only,\n" +
            "for the lucky, and the strong.\n" +
            "Just remember, in the winter,\n" +
            "Far beneath, the bitter snow,\n" +
            "Lies a seed, that with the sun's love,\n" +
            "In spring, becomes the rose...";

    public DiskTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    @Before
    public void setUp() {
        FileSystem.getFileSystem().init();
    }

    @After
    public void tearDown() {
        FileSystem.getFileSystem().flush();
    }

    @Test
    public void test() throws IOException {
        FileSystem fileSystem = FileSystem.getFileSystem();

        FilePath file_a = new FilePath("/a");
        FilePath file_b = new FilePath("/b");
        FilePath file_a_b = new FilePath("/a/b");

        System.out.println("Create dir: " + file_a);
        System.out.println("Create dir: " + file_b);
        System.out.println("Create dir: " + file_a_b);

        file_a.mkdir();
        file_b.mkdir();
        file_a_b.mkdir();

        assertTrue(file_a.exists());
        assertTrue(file_b.exists());
        assertTrue(file_a_b.exists());

        System.out.println("Root: " + Arrays.toString(fileSystem.listRoot()));
        System.out.println("/a: " + Arrays.toString(file_a.list()));

        String[] testFileNames = {
                "/a/b/a.t", "/a/b/b.t", "/a/b/c.t", "/a/b/d.t",
                "/a/b/e.t", "/a/b/f.t", "/a/b/g.t", "/a/b/h.t",
                "/a/b/i.t", "/a/b/j.t", "/a/b/k.t", "/a/b/l.t",
                "/a/b/m.t", "/a/b/n.t", "/a/b/o.t", "/a/b/p.t"
        };
        FilePath[] testFiles = new FilePath[testFileNames.length];
        for (int i = 0; i < testFileNames.length; ++i) {
            testFiles[i] = new FilePath(testFileNames[i]);
        }
        for (FilePath file : testFiles) {
            System.out.println("Create file '" + file.getPath() + "'");
            file.create();
        }
        for (FilePath file : testFiles) {
            assertTrue(file.exists());
        }

        assertEquals(1, file_a.size());
        assertEquals(0, file_b.size());
        assertEquals(testFileNames.length, file_a_b.size());

        System.out.println("/a/b: " + Arrays.toString(file_a_b.list()).replace(",", ",\n"));

        for (int i = 0; i < 64 * 2; ++i) {
            System.out.printf("%4d ", fileSystem.read(i));
            if (i % 8 == 7) {
                System.out.println();
            }
        }
        System.out.println();

        {
            FilePath p = new FilePath("/a/b/p.t");
            p.delete();
            FilePath h = new FilePath("/a/b/h.t");
            h.delete();
            assertEquals(testFileNames.length - 2, file_a_b.size());
            assertFalse(p.exists());
            assertFalse(h.exists());
            for (FilePath file : testFiles) {
                if (file.equals(p) || file.equals(h)) continue;
                assertTrue(file.exists());
            }
        }
    }

    @Test
    public void testRename() throws IOException {
        FilePath sourceFile = new FilePath("/a");
        FilePath targetFile = new FilePath("/t");
        sourceFile.mkdir();
        assertTrue(sourceFile.exists());
        assertFalse(targetFile.exists());
        sourceFile.renameTo("t");
        assertFalse(sourceFile.exists());
        assertTrue(targetFile.exists());
        targetFile.renameTo("a");
        assertFalse(targetFile.exists());
        assertTrue(sourceFile.exists());
    }

    @Test
    public void testInputAndOutputStream() throws IOException {
        System.out.println("-------- Testing input and output stream --------");
        FilePath x = new FilePath("/x");
        x.mkdir();
        FilePath y_t = new FilePath("/x/y.t");
        y_t.create();
        try (PrintWriter writer = new PrintWriter(new OutputStream(y_t))) {
            writer.print(theRose);
        }
        String data = convertStreamToString(new InputStream(y_t));
        System.out.println(data);
        assertEquals(theRose, data);

        System.out.println("--------             Finished            --------");
    }
}
