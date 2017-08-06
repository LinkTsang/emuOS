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
public class CentralProcessingUnit {
    private static final long CPU_PERIOD_MS = 100;
    private static final int INIT_TIME_SLICE = 6;
    private static final Logger LOGGER = Logger.getLogger("cpu.log");
    private final DeviceManager deviceManager = new DeviceManager();
    private final MemoryManager memoryManager = new MemoryManager();
    private final ProcessManager processManager = new ProcessManager(this, memoryManager);
    private final Timer timer = new Timer(true);
    private volatile State state = new State();
    private int time;
    private int timeSlice = 1;
    private Listener beforeStepListener;
    private Listener afterStepListener;
    private Listener intEndListener;
    private Listener intTimeSliceListener;
    private Listener intIOListener;
    private BlockingQueue<Runnable> runnableQueue = new LinkedBlockingDeque<>();

    public CentralProcessingUnit() {
        deviceManager.setFinishedHandler(deviceInfo -> runnableQueue.add(() -> state.setIntIO()));
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CentralProcessingUnit CPU = new CentralProcessingUnit();
        final boolean[] lastNullPCB = {false};
        CPU.setStepFinishedListener((cpu -> {
            StringBuilder msg = new StringBuilder("\n");
            State state = cpu.getState();
            ProcessControlBlock pcb = cpu.processManager.getRunningProcess();
            if (pcb == null) {
                if (!lastNullPCB[0]) {
                    msg.append("==== There is no any running process ====\n");
                    lastNullPCB[0] = true;
                    CentralProcessingUnit.LOGGER.info(msg.toString());
                }
            } else {
                lastNullPCB[0] = false;
                msg.append("===== CPU Stat =====\n");
                msg.append(String.format("  Current Time : %d\n", cpu.getTime()));
                msg.append(String.format("  TimeSlice    : %d\n", cpu.getTimeSlice()));
                msg.append(String.format("  Current PID  : %d\n", pcb.getPID()));
                msg.append(String.format("  Current State: %s\n", state.toString()));
                msg.append("====================\n");
                CentralProcessingUnit.LOGGER.info(msg.toString());
            }
        }));
        CPU.run();
        ProcessManager processManager = CPU.processManager;
        System.out.println("Creating processes...");
        Thread.sleep(1000);
        // It's required to create the file '/a/a.e'
        for (int i = 0; i < 5; ++i) {
            processManager.create("/a/a.e");
        }
        System.out.println("Waiting for CPU...");
        synchronized (CPU) {
            CPU.wait();
        }
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state.AX = state.AX;
        this.state.PC = state.PC;
        this.state.IR = state.IR;
        this.state.FLAGS = state.FLAGS;
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
        if (state.isIntEnd()) {
            interruptEnd();
            state.clearIntEnd();
        }
        if (state.isIntTimeSlice()) {
            interruptTime();
            state.clearTimeSlice();
        }
        if (state.isIntIO()) {
            interruptIO();
            state.clearIntIO();
        }

        if (processManager.getRunningProcess() != null) {
            state.setIR(nextByte());
            execute();
            if (--timeSlice <= 0) {
                state.setIntTimeSlice();
            }
        }
    }

    private byte nextByte() {
        return memoryManager.read(state.PC++);
    }

    private void execute() {
        int opcode = state.getIR();
        BitSet FLAGS = state.getFLAGS();
        switch (opcode) {
            case OPCODE_END: {
                state.setIntEnd();
            }
            break;
            case OPCODE_ASSIGNMENT: {
                int operand = nextByte();
                state.setAX(operand);
                if (state.getAX() == 0) {
                    FLAGS.set(State.PSW_ZF);
                }
                if (state.getAX() <= 0) {
                    FLAGS.set(State.PSW_SF);
                }
            }
            break;
            case OPCODE_INCREASE: {
                state.setAX((state.getAX() + 1) % 0xff);
                if (state.getAX() == 0) {
                    FLAGS.set(State.PSW_ZF);
                }
            }
            break;
            case OPCODE_DECREASE:
                state.setAX((state.getAX() - 1) % 0xff);
                if (state.getAX() == 0) {
                    FLAGS.set(State.PSW_ZF);
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
        LOGGER.info("\n**** INT END ****\n"
                + "  PCB  : " + processManager.getRunningProcess() + "\n"
                + "  State: " + state.toString() + "\n"
                + "*****************\n");
        if (intEndListener != null) {
            intEndListener.handle(this);
        }
        processManager.destroy(processManager.getRunningProcess());
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
                    beforeStepListener.handle(CentralProcessingUnit.this);
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
        void handle(CentralProcessingUnit cpu);
    }

    public static class State implements Cloneable {
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

        public State() {
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            State state = (State) super.clone();
            state.PSW = (BitSet) PSW.clone();
            return state;
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("State { ")
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
