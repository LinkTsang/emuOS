/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import emuos.diskmanager.FilePath;

/**
 * @author Link
 */
public class ProcessControlBlock {

    private final int PID;
    private final int startAddress;
    private CentralProcessingUnit.State CPUState;
    private ProcessState state;
    private FilePath imageFile;

    public ProcessControlBlock(int PID, int startAddress) {
        this(PID, startAddress, FilePath.NULL);
    }

    public ProcessControlBlock(int PID, int startAddress, FilePath imageFile) {
        this.PID = PID;
        this.startAddress = startAddress;
        this.imageFile = imageFile;
        state = ProcessState.READY;
        CPUState = new CentralProcessingUnit.State();
        CPUState.setPC(startAddress);
    }

    public FilePath getImageFile() {
        return imageFile;
    }

    /**
     * @return the PID
     */
    public int getPID() {
        return PID;
    }

    public void saveCPUState(CentralProcessingUnit.State state) {
        try {
            CPUState = (CentralProcessingUnit.State) state.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
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

    public CentralProcessingUnit.State getCPUState() {
        return CPUState;
    }

    @Override
    public String toString() {
        return "ProcessControlBlock{" +
                "PID=" + PID +
                ", startAddress=" + startAddress +
                ", state=" + state +
                ", imageFile=" + imageFile.getPath() +
                '}';
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
