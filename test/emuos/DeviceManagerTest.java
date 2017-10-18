/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos;

import emuos.os.DeviceManager;
import emuos.os.ProcessControlBlock;
import org.junit.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * @author Link
 */
public class DeviceManagerTest {

    public DeviceManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    private static void setPCBState(ProcessControlBlock PCB, ProcessControlBlock.ProcessState state) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = ProcessControlBlock.class.getDeclaredMethod("setState", ProcessControlBlock.ProcessState.class);
        method.setAccessible(true);
        method.invoke(PCB, state);
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        final AtomicBoolean pcbAFinished = new AtomicBoolean();
        final AtomicBoolean pcbBFinished = new AtomicBoolean();
        Semaphore semaphore = new Semaphore(-1);
        DeviceManager deviceManager = new DeviceManager();
        deviceManager.addFinishedHandler(deviceInfo -> {
            System.out.println("Finished Interrupted: ");
            System.out.println("  PID: " + deviceInfo.getPCB().getPID());
            System.out.println("  Type: " + deviceInfo.getType());
            System.out.println();
            if (deviceInfo.getPCB().getPID() == 0 && deviceInfo.getType() == 'A')
                pcbAFinished.set(true);
            if (deviceInfo.getPCB().getPID() == 1 && deviceInfo.getType() == 'B')
                pcbBFinished.set(true);
            semaphore.release();
        });

        deviceManager.start();
        ProcessControlBlock pcbA = new ProcessControlBlock(0, 0);
        setPCBState(pcbA, ProcessControlBlock.ProcessState.BLOCKED);
        DeviceManager.RequestInfo requestInfoA = new DeviceManager.RequestInfo(pcbA, 'A', 1);
        ProcessControlBlock pcbB = new ProcessControlBlock(1, 0);
        setPCBState(pcbB, ProcessControlBlock.ProcessState.BLOCKED);
        DeviceManager.RequestInfo requestInfoB = new DeviceManager.RequestInfo(pcbB, 'B', 2);
        deviceManager.alloc(requestInfoA);
        deviceManager.alloc(requestInfoB);

        try {
            assertTrue(semaphore.tryAcquire(3, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        deviceManager.stop();

        assertTrue(pcbAFinished.get());
        assertTrue(pcbBFinished.get());
    }
}
