/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.os.MemoryManager.Space;
import org.junit.*;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Link
 */
public class MemoryManagerTest {
    private emuos.os.MemoryManager memoryManager;

    public MemoryManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        memoryManager = new emuos.os.MemoryManager();
    }

    @After
    public void tearDown() {
        checkFreeListCorrect(memoryManager);
        assertTrue(memoryManager.isAllFree());
    }

    private void showMemoryUsage(emuos.os.MemoryManager manager) {
        System.out.println("------------- Memory Usage ---------------");
        System.out.println("Allocated Spaces: ");
        System.out.println("startAddress\tsize");
        for (emuos.os.MemoryManager.Space space : manager.getAllocatedSpaces()) {
            System.out.printf("%-12d\t%-4d\n", space.startAddress, space.size);
        }
        System.out.println("Free Spaces: ");
        System.out.println("startAddress\tsize");
        for (emuos.os.MemoryManager.Space space : manager.getFreeSpaces()) {
            System.out.printf("%-12d\t%-4d\n", space.startAddress, space.size);
        }
        System.out.println("------------- ------------ ---------------");
    }

    private void checkFreeListCorrect(emuos.os.MemoryManager manager) {
        PriorityQueue<emuos.os.MemoryManager.Space> queue = new PriorityQueue<>(
                Comparator.comparingInt(e0 -> e0.startAddress));
        Iterator<emuos.os.MemoryManager.Space> freeSpaceIterator = manager.getFreeSpaces().iterator();
        Iterator<emuos.os.MemoryManager.Space> allocatedSpaceIterator = manager.getAllocatedSpaces().iterator();
        while (freeSpaceIterator.hasNext()) {
            queue.add(freeSpaceIterator.next());
        }
        while (allocatedSpaceIterator.hasNext()) {
            queue.add(allocatedSpaceIterator.next());
        }
        Space prev = queue.poll();
        int totalSize = prev.size;
        while (!queue.isEmpty()) {
            Space current = queue.poll();
            totalSize += current.size;
            Assert.assertEquals(prev.startAddress + prev.size, current.startAddress);
            prev = current;
        }
        Assert.assertEquals(manager.getMaxUserSpaceSize(), prev.startAddress + prev.size);
        Assert.assertEquals(manager.getMaxUserSpaceSize(), totalSize);
    }

    @Test
    public void test0() {
        int sizeOfAddress4 = memoryManager.getMaxUserSpaceSize() - 80;
        int address0 = memoryManager.alloc(16);
        int address1 = memoryManager.alloc(32);
        int address2 = memoryManager.alloc(16);
        int address3 = memoryManager.alloc(16);
        int address4 = memoryManager.alloc(sizeOfAddress4);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);
        Assert.assertEquals(64, address3);
        Assert.assertEquals(16, memoryManager.getSpaceSize(address0));
        Assert.assertEquals(32, memoryManager.getSpaceSize(address1));
        Assert.assertEquals(16, memoryManager.getSpaceSize(address2));
        Assert.assertEquals(16, memoryManager.getSpaceSize(address3));
        Assert.assertEquals(sizeOfAddress4, memoryManager.getSpaceSize(address4));

        memoryManager.free(address2);
        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);

        memoryManager.free(address0);
        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);

        memoryManager.free(address1);
        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);

        memoryManager.free(address3);
        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);

        memoryManager.free(address4);
        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);
    }

    @Test
    public void testOutOfMemory() {
        int address0 = memoryManager.alloc(memoryManager.getMaxUserSpaceSize() + 1);
        Assert.assertEquals(-1, address0);
    }

    @Test
    public void test1() {
        int address0 = memoryManager.alloc(16);
        int address1 = memoryManager.alloc(32);
        int address2 = memoryManager.alloc(16);
        int address3 = memoryManager.alloc(16);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(64, address3);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);

        memoryManager.free(address0);
        memoryManager.free(address3);
        memoryManager.free(address2);
        memoryManager.free(address1);

        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);
    }

    @Test
    public void test2() {
        int address0 = memoryManager.alloc(16);
        int address1 = memoryManager.alloc(32);
        int address2 = memoryManager.alloc(16);
        int address3 = memoryManager.alloc(16);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);
        Assert.assertEquals(64, address3);

        memoryManager.free(address3);
        memoryManager.free(address1);
        memoryManager.free(address0);
        memoryManager.free(address2);

        checkFreeListCorrect(memoryManager);
        showMemoryUsage(memoryManager);
    }

    @Test
    public void testReadWrite() {
        int address = memoryManager.alloc(16);
        memoryManager.write(address, (byte) 42);
        assertEquals(42, memoryManager.read(address));

        memoryManager.writeInt(address + 8, 42);
        assertEquals(42, memoryManager.readInt(address + 8));

        memoryManager.free(address);
    }

    @Test
    public void testException() {
        try {
            memoryManager.alloc(0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        try {
            memoryManager.free(-1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testContinuousAlloc() {
        int addresses[] = new int[4];
        for (int i = 0; i < addresses.length; ++i) {
            addresses[i] = memoryManager.alloc(16);
        }
        showMemoryUsage(memoryManager);
        for (int address : addresses) {
            memoryManager.free(address);
            showMemoryUsage(memoryManager);
            checkFreeListCorrect(memoryManager);
        }
    }
}
