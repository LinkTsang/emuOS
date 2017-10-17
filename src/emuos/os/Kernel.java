/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import emuos.compiler.Instruction;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import static emuos.compiler.Instruction.*;

/**
 * @author Link
 */
public class Kernel implements Closeable {
    public static final long CPU_PERIOD_MS = 500;
    public static final int INIT_TIME_SLICE = 6;
    private static final Logger LOGGER = Logger.getLogger("kernel.log");
    private final DeviceManager deviceManager = new DeviceManager();
    private final MemoryManager memoryManager = new MemoryManager();
    private final ProcessManager processManager = new ProcessManager(this, memoryManager);
    private final Timer timer = new Timer(true);
    private final Collection<Listener> beginOperationListeners = new LinkedList<>();
    private final Collection<Listener> intExitListeners = new LinkedList<>();
    private final Collection<Listener> intTimeSliceListeners = new LinkedList<>();
    private final Collection<Listener> intIOListeners = new LinkedList<>();
    private volatile Context context = new Context();
    private long time;
    private long executionTime;
    private int timeSlice = 1;
    private Listener afterStepListener;
    private BlockingQueue<Runnable> runnableQueue = new LinkedBlockingDeque<>();
    private DeviceManager.Handler deviceIOFinishedHandler = deviceInfo -> runLater(() -> context.setIntIO());

    /**
     * ctor
     */
    public Kernel() {
        deviceManager.addFinishedHandler(deviceIOFinishedHandler);
    }

    /**
     * main
     *
     * @param args args
     * @throws InterruptedException            InterruptedException
     * @throws IOException                     InterruptedException
     * @throws ProcessManager.ProcessException InterruptedException
     */
    public static void main(String[] args) throws InterruptedException, IOException, ProcessManager.ProcessException {
        try (Kernel kernel = new Kernel()) {
            final boolean[] lastNullPCB = {false};
            kernel.addBeginOperationListener((k -> {
                StringBuilder msg = new StringBuilder("\n");
                Context context = k.getContext();
                ProcessControlBlock pcb = k.processManager.getRunningProcess();
                if (pcb == null) {
                    if (!lastNullPCB[0]) {
                        msg.append("==== There is no any running process ====\n");
                        lastNullPCB[0] = true;
                        Kernel.LOGGER.info(msg.toString());
                    }
                } else {
                    lastNullPCB[0] = false;
                    msg.append("===== CPU Stat =====\n");
                    msg.append(String.format("  Current Time : %d\n", k.getTime()));
                    msg.append(String.format("  TimeSlice    : %d\n", k.getTimeSlice()));
                    msg.append(String.format("  Current PID  : %d\n", pcb.getPID()));
                    msg.append(String.format("  Context: %s\n", context.toString()));
                    msg.append("====================\n");
                    Kernel.LOGGER.info(msg.toString());
                }
            }));
            kernel.run();
            ProcessManager processManager = kernel.processManager;
            System.out.println("Creating processes...");
            Thread.sleep(1000);
            // It's required to create the file '/a/a.e'
            for (int i = 0; i < 5; ++i) {
                processManager.create("/a/a.e");
            }
            System.out.println("Waiting for CPU...");
            synchronized (kernel) {
                kernel.wait();
            }
        }
    }

    /**
     * @return DeviceManager
     */
    public DeviceManager getDeviceManager() {
        return deviceManager;
    }

    /**
     * @return MemoryManager
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * @return ProcessManager
     */
    public ProcessManager getProcessManager() {
        return processManager;
    }

    /**
     * add IntExitListener
     *
     * @param listener IntExitListener
     * @return <tt>true</tt> if the listener was added as a result of the call
     */
    public boolean addIntExitListener(Listener listener) {
        synchronized (intExitListeners) {
            return intExitListeners.add(listener);
        }
    }

    /**
     * add IntTimeSliceListener
     *
     * @param listener IntTimeSliceListener
     * @return <tt>true</tt> if the listener was added as a result of the call
     */
    public boolean addIntTimeSliceListener(Listener listener) {
        synchronized (intTimeSliceListeners) {
            return intTimeSliceListeners.add(listener);
        }
    }

    /**
     * add IntIOListener
     *
     * @param listener IntIOListener
     * @return <tt>true</tt> if the listener was added as a result of the call
     */
    public boolean addIntIOListener(Listener listener) {
        synchronized (intIOListeners) {
            return intIOListeners.add(listener);
        }
    }


    /**
     * remove IntExitListener
     *
     * @param listener IntExitListener
     * @return <tt>true</tt> if an element was removed as a result of this call
     */
    public boolean removeIntExitListener(Listener listener) {
        synchronized (intExitListeners) {
            return intExitListeners.remove(listener);
        }
    }

    /**
     * remove IntTimeSliceListener
     *
     * @param listener IntTimeSliceListener
     * @return <tt>true</tt> if an element was removed as a result of this call
     */
    public boolean removeIntTimeSliceListener(Listener listener) {
        synchronized (intTimeSliceListeners) {
            return intTimeSliceListeners.remove(listener);
        }
    }

    /**
     * remove IntIOListener
     *
     * @param listener IntIOListener
     * @return <tt>true</tt> if an element was removed as a result of this call
     */
    public boolean removeIntIOListener(Listener listener) {
        synchronized (intIOListeners) {
            return intIOListeners.remove(listener);
        }
    }

    /**
     * @return the context
     */
    Context getContext() {
        return context;
    }

    /**
     * set the context
     *
     * @param context context
     */
    void setContext(Context context) {
        this.context.AX = context.AX;
        this.context.PC = context.PC;
        this.context.IR = context.IR;
        this.context.FLAGS = context.FLAGS;
    }

    /**
     * @return current context
     */
    public Context snapContext() {
        // FIXME: synchronization?
        return context.clone();
    }

    /**
     * set BeforeStepListener
     *
     * @param listener BeforeStepListener
     */
    private boolean addBeginOperationListener(Listener listener) {
        synchronized (beginOperationListeners) {
            return beginOperationListeners.add(listener);
        }
    }

    /**
     * set AfterStepListener
     *
     * @param listener AfterStepListener
     * @return the old listener
     */
    private synchronized Listener setAfterStepListener(Listener listener) {
        Listener oldListener = afterStepListener;
        afterStepListener = listener;
        return oldListener;
    }

    private void CPU() {
        time++;
        if (context.isIntEnd()) {
            interruptEnd();
            context.clearIntEnd();
        }
        if (context.isIntTimeSlice()) {
            interruptTime();
            context.clearTimeSlice();
        }
        if (context.isIntIO()) {
            interruptIO();
            context.clearIntIO();
        }

        if (processManager.getRunningProcess() != null) {
            context.setIR(nextByte());
            execute();
            if (--timeSlice <= 0) {
                context.setIntTimeSlice();
            }
        }
    }

    private byte nextByte() {
        return memoryManager.read(context.PC++);
    }

    private void execute() {
        int opcode = context.getIR();
        BitSet FLAGS = context.getFLAGS();
        switch (opcode) {
            case OPCODE_END: {
                context.setIntEnd();
            }
            break;
            case OPCODE_ASSIGNMENT: {
                int operand = nextByte();
                context.setAX(operand);
                if (context.getAX() == 0) {
                    FLAGS.set(Context.PSW_ZF);
                }
                if (context.getAX() < 0) {
                    FLAGS.set(Context.PSW_SF);
                }
            }
            break;
            case OPCODE_INCREASE: {
                context.setAX((context.getAX() + 1) % 0xff);
                if (context.getAX() == 0) {
                    FLAGS.set(Context.PSW_ZF);
                }
            }
            break;
            case OPCODE_DECREASE:
                context.setAX((context.getAX() - 1) % 0xff);
                if (context.getAX() == 0) {
                    FLAGS.set(Context.PSW_ZF);
                }
                break;
            case OPCODE_IO: {
                LOGGER.info("\n**** IO REQ ****\n");
                int deviceID = nextByte();
                int usageTime = nextByte();
                ProcessControlBlock process = processManager.getRunningProcess();
                DeviceManager.RequestInfo requestInfo = new DeviceManager.RequestInfo(
                        process, deviceID, usageTime);
                deviceManager.alloc(requestInfo);
                processManager.block(process);
            }
            break;
            default:
                break;
        }
        if (opcode != OPCODE_IO) ++executionTime;
    }

    private void interruptEnd() {
        ProcessControlBlock pcb = processManager.getRunningProcess();
        LOGGER.info("\n**** INT END ****\n"
                + "  PCB  : " + pcb + "\n"
                + "  Context: " + context.toString() + "\n"
                + "*****************\n");
        pcb.saveContext(context);
        intExitListeners.forEach(listener -> listener.handle(this));
        processManager.destroy(pcb);
    }

    private void interruptTime() {
        LOGGER.info("\n**** INT TIME SLICE ****\n");
        intTimeSliceListeners.forEach(listener -> listener.handle(this));
        processManager.schedule();
    }

    private void interruptIO() {
        LOGGER.info("\n**** INT IO ****\n");
        intIOListeners.forEach(listener -> listener.handle(this));
        BlockingQueue<ProcessControlBlock> queue = deviceManager.getFinishedQueue();
        ProcessControlBlock pcb;
        while ((pcb = queue.poll()) != null) {
            processManager.awake(pcb);
        }
    }

    /**
     * run the kernel
     */
    public void run() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (processManager.getRunningProcess() == null) {
                    processManager.schedule();
                }
                beginOperationListeners.forEach(listener -> listener.handle(Kernel.this));
                CPU();
                Runnable runnable;
                while ((runnable = runnableQueue.poll()) != null) {
                    runnable.run();
                }
            }
        }, 0, CPU_PERIOD_MS);
        deviceManager.start();
    }

    /**
     * stop the kernel
     */
    public void stop() {
        deviceManager.stop();
        timer.cancel();
        timer.purge();
    }

    /**
     * Run a runnable object in the kernel thread
     *
     * @param runnable runnable task
     */
    private void runLater(Runnable runnable) {
        runnableQueue.add(runnable);
    }

    /**
     * @return the current time
     */
    public long getTime() {
        return time;
    }

    /**
     * @return the current time slice
     */
    public int getTimeSlice() {
        return timeSlice;
    }

    void resetTimeSlice() {
        timeSlice = INIT_TIME_SLICE;
    }

    @Override
    public void close() {
        deviceManager.removeFinishedHandler(deviceIOFinishedHandler);
    }

    /**
     * @return execution time
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * kernel Event Listener
     */
    public interface Listener {
        void handle(Kernel kernel);
    }

    /**
     * the Context class
     */
    public static class Context implements Cloneable {
        public static final int PSW_INT_END = 9;
        public static final int PSW_INT_TIME_SLICE = 8;
        public static final int PSW_INT_IO = 7;
        public static final int PSW_CF = 3;
        public static final int PSW_SF = 2;
        public static final int PSW_OF = 1;
        public static final int PSW_ZF = 0;
        private int AX;
        private BitSet PSW = new BitSet(10);
        private BitSet FLAGS = new BitSet(8);
        private int IR;         // last Instruction
        private int PC;         // next PC

        Context() {
        }

        @Override
        protected Context clone() {
            try {
                Context context = (Context) super.clone();
                context.PSW = (BitSet) PSW.clone();
                return context;
            } catch (CloneNotSupportedException e) {
                e.printStackTrace();
                throw new AssertionError();
            }
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Context { ")
                    .append("AX=").append(AX)
                    .append(", PC=").append(PC)
                    .append(", IR=").append(Instruction.getName(IR));
            stringBuilder.append(", FLAGS={ ");
            if (FLAGS.get(PSW_CF)) stringBuilder.append("CF ");
            if (FLAGS.get(PSW_SF)) stringBuilder.append("SF ");
            if (FLAGS.get(PSW_OF)) stringBuilder.append("OF ");
            if (FLAGS.get(PSW_ZF)) stringBuilder.append("ZF ");
            stringBuilder.append("}");
            stringBuilder.append(", PSW={ ");
            if (isIntIO()) stringBuilder.append("INT_IO ");
            if (isIntTimeSlice()) stringBuilder.append("INT_TIME_SLICE ");
            if (isIntEnd()) stringBuilder.append("INT_END ");
            stringBuilder.append("}}");
            return stringBuilder.toString();
        }

        public int getAX() {
            return AX;
        }

        void setAX(int AX) {
            this.AX = AX;
        }

        public BitSet getPSW() {
            return PSW;
        }

        public BitSet getFLAGS() {
            return FLAGS;
        }

        public boolean isIntEnd() {
            return PSW.get(PSW_INT_END);
        }

        public boolean isIntTimeSlice() {
            return PSW.get(PSW_INT_TIME_SLICE);
        }

        public boolean isIntIO() {
            return PSW.get(PSW_INT_IO);
        }

        void clearIntEnd() {
            PSW.clear(PSW_INT_END);
        }

        void clearTimeSlice() {
            PSW.clear(PSW_INT_TIME_SLICE);
        }

        void clearIntIO() {
            PSW.clear(PSW_INT_IO);
        }

        void setIntEnd() {
            PSW.set(PSW_INT_END);
        }

        void setIntTimeSlice() {
            PSW.set(PSW_INT_TIME_SLICE);
        }

        void setIntIO() {
            PSW.set(PSW_INT_IO);
        }

        public int getIR() {
            return IR;
        }

        void setIR(int IR) {
            this.IR = IR;
        }

        public int getPC() {
            return PC;
        }

        void setPC(int PC) {
            this.PC = PC;
        }

    }
}
