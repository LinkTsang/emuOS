/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.io.IOException;
import java.util.BitSet;
import java.util.Timer;
import java.util.TimerTask;

import static emuos.compiler.Instruction.*;

/**
 * @author Link
 */
public class CentralProcessingUnit {
    private static final long CPU_PERIOD_MS = 1000;
    private static final int INIT_TIME_SLICE = 6;
    private final DeviceManager deviceManager = new DeviceManager();
    private final MemoryManager memoryManager = new MemoryManager();
    private final ProcessManager processManager = new ProcessManager(this, memoryManager);
    private final Timer timer = new Timer(true);
    private final State state = new State();
    private int time;
    private int timeSlice = 1;
    private Listener stepFinishedListener;

    public CentralProcessingUnit() {
        deviceManager.setFinishedHandler(deviceInfo -> processManager.awake(deviceInfo.getPCB()));
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CentralProcessingUnit CPU = new CentralProcessingUnit();
        final boolean[] lastNullPCB = {false};
        CPU.setStepFinishedListener((cpu -> {
            State state = cpu.getState();
            ProcessControlBlock pcb = cpu.processManager.getRunningProcess();
            if (pcb == null) {
                if (!lastNullPCB[0]) {
                    System.out.println("No any process is running...");
                    System.out.println("----------");
                    lastNullPCB[0] = true;
                }
            } else {
                System.out.printf(" CPU stat (Current Time: %d)\n", cpu.getTime());
                System.out.printf("Current PID: %d\n", pcb == null ? 0 : pcb.getPID());
                System.out.printf("TimeSlice = %d\n", cpu.getTimeSlice());
                System.out.printf("AX = %d, PC = %d, PSW = %s, FLAGS = %s\n",
                        state.getAX(),
                        state.getPC(),
                        state.getPSW().toString(),
                        state.getFLAGS().toString()
                );
                System.out.println("----------");
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
        synchronized (CPU) {
            CPU.wait();
        }
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

    public Listener setStepFinishedListener(Listener listener) {
        Listener oldListener = stepFinishedListener;
        stepFinishedListener = listener;
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
        } else {
            processManager.schedule();
        }

        if (stepFinishedListener != null) {
            stepFinishedListener.handle(this);
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
                int deviceID = nextByte();
                int usageTime = nextByte();
                DeviceManager.RequestInfo requestInfo = new DeviceManager.RequestInfo(
                        processManager.getRunningProcess(), deviceID, usageTime);
                deviceManager.alloc(requestInfo);
            }
            break;
            default:
                break;
        }

    }

    private void interruptEnd() {
        processManager.destroy(processManager.getRunningProcess());
    }

    private void interruptTime() {
        processManager.schedule();
    }

    private void interruptIO() {

    }

    public void run() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                CPU();
            }
        }, 0, CPU_PERIOD_MS);
    }

    public void stop() {
        timer.cancel();
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
        private int IR;
        private int PC;

        public State() {
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            State state = (State) super.clone();
            state.PSW = (BitSet) PSW.clone();
            return state;
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
