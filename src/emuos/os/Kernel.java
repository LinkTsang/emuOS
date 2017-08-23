/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import emuos.compiler.Instruction;

import java.io.IOException;
import java.util.BitSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Logger;

import static emuos.compiler.Instruction.*;

/**
 * @author Link
 */
public class Kernel {
    private static final long CPU_PERIOD_MS = 100;
    private static final int INIT_TIME_SLICE = 6;
    private static final Logger LOGGER = Logger.getLogger("kernel.log");
    private final DeviceManager deviceManager = new DeviceManager();
    private final MemoryManager memoryManager = new MemoryManager();
    private final ProcessManager processManager = new ProcessManager(this, memoryManager);
    private final Timer timer = new Timer(true);
    private volatile Context context = new Context();
    private int time;
    private int timeSlice = 1;
    private Listener beforeStepListener;
    private Listener afterStepListener;
    private Listener intEndListener;
    private Listener intTimeSliceListener;
    private Listener intIOListener;
    private BlockingQueue<Runnable> runnableQueue = new LinkedBlockingDeque<>();

    public Kernel() {
        deviceManager.setFinishedHandler(deviceInfo -> runnableQueue.add(() -> context.setIntIO()));
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        Kernel kernel = new Kernel();
        final boolean[] lastNullPCB = {false};
        kernel.setStepFinishedListener((k -> {
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

    public DeviceManager getDeviceManager() {
        return deviceManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }

    public synchronized Listener setIntEndListener(Listener listener) {
        Listener oldListener = intEndListener;
        intEndListener = listener;
        return oldListener;
    }

    public synchronized Listener setIntTimeSliceListener(Listener listener) {
        Listener oldListener = intTimeSliceListener;
        intTimeSliceListener = listener;
        return oldListener;
    }

    public synchronized Listener setIntIOListener(Listener listener) {
        Listener oldListener = intIOListener;
        intIOListener = listener;
        return oldListener;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context.AX = context.AX;
        this.context.PC = context.PC;
        this.context.IR = context.IR;
        this.context.FLAGS = context.FLAGS;
    }

    public synchronized Listener setStepFinishedListener(Listener listener) {
        Listener oldListener = beforeStepListener;
        beforeStepListener = listener;
        return oldListener;
    }

    public synchronized Listener setAfterStepListener(Listener listener) {
        Listener oldListener = afterStepListener;
        afterStepListener = listener;
        return oldListener;
    }

    public void CPU() {
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

    }

    private void interruptEnd() {
        ProcessControlBlock pcb = processManager.getRunningProcess();
        LOGGER.info("\n**** INT END ****\n"
                + "  PCB  : " + pcb + "\n"
                + "  Context: " + context.toString() + "\n"
                + "*****************\n");
        pcb.saveContext(context);
        if (intEndListener != null) {
            intEndListener.handle(this);
        }
        processManager.destroy(pcb);
    }

    private void interruptTime() {
        LOGGER.info("\n**** INT TIME SLICE ****\n");
        if (intTimeSliceListener != null) {
            intTimeSliceListener.handle(this);
        }
        processManager.schedule();
    }

    private void interruptIO() {
        LOGGER.info("\n**** INT IO ****\n");
        if (intIOListener != null) {
            intIOListener.handle(this);
        }
        BlockingQueue<ProcessControlBlock> queue = deviceManager.getFinishedQueue();
        ProcessControlBlock pcb;
        while ((pcb = queue.poll()) != null) {
            processManager.awake(pcb);
        }
    }

    public void run() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (processManager.getRunningProcess() == null) {
                    processManager.schedule();
                }
                if (beforeStepListener != null) {
                    beforeStepListener.handle(Kernel.this);
                }
                CPU();
                Runnable runnable;
                while ((runnable = runnableQueue.poll()) != null) {
                    runnable.run();
                }
            }
        }, 0, CPU_PERIOD_MS);
        deviceManager.start();
    }

    public void stop() {
        deviceManager.stop();
        timer.cancel();
        timer.purge();
    }

    public void runLater(Runnable runnable) {
        runnableQueue.add(runnable);
    }

    /**
     * @return the time
     */
    public int getTime() {
        return time;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    public void resetTimeSlice() {
        timeSlice = INIT_TIME_SLICE;
    }

    public interface Listener {
        void handle(Kernel kernel);
    }

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

        public Context() {
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            Context context = (Context) super.clone();
            context.PSW = (BitSet) PSW.clone();
            return context;
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

        public void setAX(int AX) {
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

        public void clearIntEnd() {
            PSW.clear(PSW_INT_END);
        }

        public void clearTimeSlice() {
            PSW.clear(PSW_INT_TIME_SLICE);
        }

        public void clearIntIO() {
            PSW.clear(PSW_INT_IO);
        }

        public void setIntEnd() {
            PSW.set(PSW_INT_END);
        }

        public void setIntTimeSlice() {
            PSW.set(PSW_INT_TIME_SLICE);
        }

        public void setIntIO() {
            PSW.set(PSW_INT_IO);
        }

        public int getIR() {
            return IR;
        }

        public void setIR(int IR) {
            this.IR = IR;
        }

        public int getPC() {
            return PC;
        }

        public void setPC(int PC) {
            this.PC = PC;
        }

    }
}
