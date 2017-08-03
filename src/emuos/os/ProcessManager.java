/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.InputStream;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Link
 */
public class ProcessManager {

    private final BlockingQueue<ProcessControlBlock> blockedQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ProcessControlBlock> readyQueue = new LinkedBlockingQueue<>();
    private final CentralProcessingUnit cpu;
    private final MemoryManager memoryManager;
    private ProcessControlBlock runningProcess;
    private int nextPID = 1;

    public ProcessManager(CentralProcessingUnit cpu, MemoryManager memoryManager) {
        this.cpu = cpu;
        this.memoryManager = memoryManager;
    }

    public synchronized ProcessControlBlock create(String path) throws IOException {
        FilePath imageFile = new FilePath(path);
        if (!imageFile.exists() || !imageFile.isFile()) return null;
        int imageSize = imageFile.size();

        int address = memoryManager.alloc(imageSize);
        if (address < -1) {
            throw new RuntimeException("There is not enough memory to allocate for the new process.");
        }

        try (InputStream inputStream = new InputStream(imageFile)) {
            int value, currentPos = address;
            while ((value = inputStream.read()) != -1) {
                memoryManager.write(currentPos++, (byte) value);
            }
        }

        ProcessControlBlock PCB = new ProcessControlBlock(nextPID++, address);
        if (!memoryManager.addPCB(PCB)) {
            memoryManager.free(address);
            throw new RuntimeException("There is not enough PCB spaces for the new process.");
        }
        getReadyQueue().add(PCB);
        return PCB;
    }

    public synchronized void destroy(ProcessControlBlock PCB) {
        if (PCB.equals(runningProcess)) {
            runningProcess = null;
        }
        memoryManager.free(PCB.getStartAddress());
        if (!memoryManager.removePCB(PCB)) {
            throw new RuntimeException("There is no such PCB.");
        }
        if (!readyQueue.remove(PCB)) {
            // FIXME: stop IO?
            blockedQueue.remove(PCB);
        }
        schedule();
    }

    public synchronized void block(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessControlBlock.ProcessState.READY) {
            getReadyQueue().remove(PCB);
            PCB.setState(ProcessControlBlock.ProcessState.BLOCKED);
            getBlockedQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    public synchronized void awake(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessControlBlock.ProcessState.BLOCKED) {
            getBlockedQueue().remove(PCB);
            PCB.setState(ProcessControlBlock.ProcessState.READY);
            getReadyQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    public synchronized void schedule() {
        if (runningProcess != null) {
            runningProcess.saveCPUState(cpu.getState());
            readyQueue.add(runningProcess);
        }
        runningProcess = readyQueue.poll();
        cpu.resetTimeSlice();
        if (runningProcess != null) {
            cpu.setState(runningProcess.getCPUState());
        }
    }

    /**
     * @return the blockedQueue
     */
    public BlockingQueue<ProcessControlBlock> getBlockedQueue() {
        return blockedQueue;
    }

    /**
     * @return the readyQueue
     */
    public BlockingQueue<ProcessControlBlock> getReadyQueue() {
        return readyQueue;
    }

    /**
     * @return the running process
     */
    public ProcessControlBlock getRunningProcess() {
        return runningProcess;
    }
}
