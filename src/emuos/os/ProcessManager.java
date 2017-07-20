/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Link
 */
public class ProcessManager {

    private final Queue<ProcessControlBlock> blockedQueue = new LinkedList<>();
    private final Queue<ProcessControlBlock> readyQueue = new LinkedList<>();
    private final MemoryManager memoryManager;
    private final CentralProcessingUnit CPU;
    private int nextPID = 0;

    public ProcessManager(CentralProcessingUnit CPU, MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.CPU = CPU;
    }

    public ProcessControlBlock create(String Path) {
        int address = memoryManager.alloc(32);
        if (address < -1) {
            throw new RuntimeException("There is not enough memory to allocate for the new process.");
        }
        ProcessControlBlock PCB = new ProcessControlBlock(nextPID++, address);
        if (!memoryManager.addPCB(PCB)) {
            memoryManager.free(address);
            throw new RuntimeException("There is not enough PCB spaces for the new process.");
        }
        getReadyQueue().add(PCB);
        return PCB;
    }

    public void destroy(ProcessControlBlock PCB) {
        memoryManager.free(PCB.getStartAddress());
        if (!memoryManager.removePCB(PCB)) {
            throw new RuntimeException("There is no such PCB.");
        }
        if (!readyQueue.remove(PCB)) {
            blockedQueue.remove(PCB);
        }
    }

    public void block(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessControlBlock.ProcessState.READY) {
            getReadyQueue().remove(PCB);
            PCB.setState(ProcessControlBlock.ProcessState.BLOCKED);
            getBlockedQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    public void awake(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessControlBlock.ProcessState.BLOCKED) {
            getBlockedQueue().remove(PCB);
            PCB.setState(ProcessControlBlock.ProcessState.READY);
            getReadyQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    /**
     * @return the blockedQueue
     */
    public Queue<ProcessControlBlock> getBlockedQueue() {
        return blockedQueue;
    }

    /**
     * @return the readyQueue
     */
    public Queue<ProcessControlBlock> getReadyQueue() {
        return readyQueue;
    }
}
