/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.util.BitSet;
import java.util.Timer;
import java.util.TimerTask;

import static emuos.generator.Instruction.*;

/**
 * @author Link
 */
public class CentralProcessingUnit {

    private static final int PSW_INT_END = 9;
    private static final int PSW_INT_TIME_SLICE = 8;
    private static final int PSW_INT_IO = 7;
    private static final int PSW_CF = 3;
    private static final int PSW_SF = 2;
    private static final int PSW_OF = 1;
    private static final int PSW_ZF = 0;
    private final MemoryManager memoryManager = new MemoryManager();
    private final ProcessManager processManager = new ProcessManager(this, memoryManager);
    private final Timer timer = new Timer(false);
    private int AX;
    private BitSet PSW = new BitSet(10);
    private int IR;
    private int PC;
    private int time;
    private int timeslice = 1;
    private Listener onFinishedListener;

    public static void main(String[] args) {
        CentralProcessingUnit CPU = new CentralProcessingUnit();
        CPU.setOnFinishedListener((cpu -> {
            System.out.printf(" CPU stat (Current Time: %d)\n", cpu.getTime());
            System.out.printf("TimeSlice = %d\n", cpu.getTimeSlice());
            System.out.printf("AX = %d, PC = %d, PSW = %s\n", cpu.getAX(), cpu.getPC(), cpu.getPSW().toString());
            System.out.println("----------");
        }));
        CPU.run();
    }

    public Listener setOnFinishedListener(Listener listener) {
        Listener oldListener = onFinishedListener;
        onFinishedListener = listener;
        return oldListener;
    }

    public void CPU() {
        time++;
        if (PSW.get(PSW_INT_END)) {
            interruptEnd();
            PSW.clear(PSW_INT_END);
        }
        if (PSW.get(PSW_INT_TIME_SLICE)) {
            interruptTime();
            PSW.clear(PSW_INT_TIME_SLICE);
        }
        if (PSW.get(PSW_INT_IO)) {
            interruptIO();
            PSW.clear(PSW_INT_IO);
        }

        IR = nextByte();
        execute();
        if (--timeslice == 0) {
            PSW.set(PSW_INT_TIME_SLICE);
        }

        if (onFinishedListener != null) {
            onFinishedListener.on(this);
        }
    }

    private byte nextByte() {
        return memoryManager.read(PC++);
    }

    private void execute() {
        int opcode = IR >>> 24;

        switch (opcode) {
            case OPCODE_END: {
                PSW.set(PSW_INT_END);
            }
            break;
            case OPCODE_ASSIGNMENT: {
                //int operand = IR & 0x000000ff;
                int operand = nextByte();
                AX = operand;
                if (AX == 0) {
                    PSW.set(PSW_ZF);
                }
                if (AX <= 0) {
                    PSW.set(PSW_SF);
                }
            }
            break;
            case OPCODE_INCREASE: {
                AX = (AX + 1) % 0xff;
                if (AX == 0) {
                    PSW.set(PSW_ZF);
                }
            }
            break;
            case OPCODE_DECREASE:
                AX = (AX - 1) % 0xff;
                if (AX == 0) {
                    PSW.set(PSW_ZF);
                }
                break;
            case OPCODE_IO: {
                //int deviceID = (IR & 0x00ff0000) >> 16;
                //int usageTime = (IR & 0x0000ff00) >> 8;
                int deviceID = nextByte();
                int usageTime = nextByte();
                System.out.println("IO: " + deviceID + " " + usageTime);
            }
            break;
            default:
                break;
        }

    }

    private void interruptEnd() {

    }

    private void interruptTime() {
        timeslice = 6;
    }

    private void interruptIO() {

    }

    public void run() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                CPU();
            }
        }, 0, 500);
    }

    /**
     * @return the time
     */
    public int getTime() {
        return time;
    }

    /**
     * @return the AX
     */
    public int getAX() {
        return AX;
    }

    /**
     * @return the PSW
     */
    public BitSet getPSW() {
        return PSW;
    }

    /**
     * @return the IR
     */
    public int getIR() {
        return IR;
    }

    /**
     * @return the PC
     */
    public int getPC() {
        return PC;
    }

    public void recoveryState(ProcessControlBlock PCB) {
        this.AX = PCB.getAX();
        this.PC = PCB.getPC();
        this.PSW = PCB.getPSW();
    }

    public int getTimeSlice() {
        return timeslice;
    }

    public interface Listener {
        void on(CentralProcessingUnit cpu);
    }
}
