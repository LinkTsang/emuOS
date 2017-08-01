/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.os.MemoryManager.Space;
import org.junit.*;

import java.util.Iterator;
import java.util.PriorityQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Link
 */
public class MemoryManagerTest {

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
    }

    @After
    public void tearDown() {
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

    private boolean isFreeListCorrect(emuos.os.MemoryManager manager) {
        PriorityQueue<emuos.os.MemoryManager.Space> queue = new PriorityQueue((e0, e1) -> ((Space) e0).startAddress - ((Space) e1).startAddress);
        Iterator<emuos.os.MemoryManager.Space> freeSpaceIterator = manager.getFreeSpaces().iterator();
        Iterator<emuos.os.MemoryManager.Space> allocatedSpaceIterator = manager.getAllocatedSpaces().iterator();
        while (freeSpaceIterator.hasNext()) {
            queue.add(freeSpaceIterator.next());
        }
        while (allocatedSpaceIterator.hasNext()) {
            queue.add(allocatedSpaceIterator.next());
        }
        Space prev = queue.poll();
        while (!queue.isEmpty()) {
            Space current = queue.poll();
            if (prev.startAddress + prev.size != current.startAddress) {
                return false;
            }
            prev = current;
        }
        return prev.startAddress + prev.size == manager.getMaxUserSpaceSize();
    }

    @Test
    public void test0() {
        emuos.os.MemoryManager manager = new emuos.os.MemoryManager();
        int address0 = manager.alloc(16);
        int address1 = manager.alloc(32);
        int address2 = manager.alloc(16);
        int address3 = manager.alloc(16);
        int address4 = manager.alloc(manager.getMaxUserSpaceSize() - 80);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);
        Assert.assertEquals(64, address3);

        manager.free(address2);
        isFreeListCorrect(manager);
        showMemoryUsage(manager);

        manager.free(address0);
        isFreeListCorrect(manager);
        showMemoryUsage(manager);

        manager.free(address1);
        isFreeListCorrect(manager);
        showMemoryUsage(manager);

        manager.free(address3);
        isFreeListCorrect(manager);
        showMemoryUsage(manager);

        manager.free(address4);
        isFreeListCorrect(manager);
        showMemoryUsage(manager);

        assertTrue(manager.isAllFree());
    }

    @Test
    public void testOutOfMemory() {
        emuos.os.MemoryManager manager = new emuos.os.MemoryManager();
        int address0 = manager.alloc(manager.getMaxUserSpaceSize() + 1);
        Assert.assertEquals(-1, address0);
        assertTrue(manager.isAllFree());
    }

    @Test
    public void test1() {
        emuos.os.MemoryManager manager = new emuos.os.MemoryManager();
        int address0 = manager.alloc(16);
        int address1 = manager.alloc(32);
        int address2 = manager.alloc(16);
        int address3 = manager.alloc(16);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(64, address3);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);

        manager.free(address0);
        manager.free(address3);
        manager.free(address2);
        manager.free(address1);

        isFreeListCorrect(manager);
        showMemoryUsage(manager);
        assertTrue(manager.isAllFree());
    }

    @Test
    public void test2() {
        emuos.os.MemoryManager manager = new emuos.os.MemoryManager();
        int address0 = manager.alloc(16);
        int address1 = manager.alloc(32);
        int address2 = manager.alloc(16);
        int address3 = manager.alloc(16);
        Assert.assertEquals(0, address0);
        Assert.assertEquals(16, address1);
        Assert.assertEquals(48, address2);
        Assert.assertEquals(64, address3);

        manager.free(address3);
        manager.free(address1);
        manager.free(address0);
        manager.free(address2);

        isFreeListCorrect(manager);
        showMemoryUsage(manager);
        assertTrue(manager.isAllFree());
    }

    @Test
    public void testReadWrite() {
        emuos.os.MemoryManager manager = new emuos.os.MemoryManager();
        int address = manager.alloc(16);
        manager.write(address, (byte) 42);
        assertEquals(42, manager.read(address));

        manager.writeInt(address + 8, 42);
        assertEquals(42, manager.readInt(address + 8));

        manager.free(address);
    }

    @Test
    public void testException() {
        try {
            new emuos.os.MemoryManager().alloc(0);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        try {
            new emuos.os.MemoryManager().free(-1);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
