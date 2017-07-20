/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.util.BitSet;

/**
 * @author Link
 */
public class ProcessControlBlock {

    private final int PID;
    private final int startAddress;
    private int AX;
    private BitSet PSW;
    private int PC;
    private ProcessState state;

    public ProcessControlBlock(int PID, int startAddress) {
        this.PID = PID;
        this.startAddress = startAddress;
    }

    /**
     * @return the PID
     */
    public int getPID() {
        return PID;
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
     * @return the PC
     */
    public int getPC() {
        return PC;
    }

    public void saveState(CentralProcessingUnit CPU) {
        this.AX = CPU.getAX();
        this.PC = CPU.getPC();
        this.PSW = CPU.getPSW();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProcessControlBlock)) {
            return false;
        }
        ProcessControlBlock t = (ProcessControlBlock) o;
        return this.PID == t.PID;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + this.PID;
        return hash;
    }

    /**
     * @return the state
     */
    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    /**
     * @return the startAddress
     */
    public int getStartAddress() {
        return startAddress;
    }

    public enum BlockReason {

    }

    public enum ProcessState {
        RUNNING,
        READY,
        BLOCKED,
        TERMINAL,
    }
}
