/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import emuos.diskmanager.FilePath;
import emuos.diskmanager.InputStream;
import emuos.os.Kernel.Context;
import emuos.os.ProcessControlBlock.ProcessState;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Link
 */
public class ProcessManager {

    private final BlockingQueue<ProcessControlBlock> blockedQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ProcessControlBlock> readyQueue = new LinkedBlockingQueue<>();
    private final Kernel kernel;
    private final MemoryManager memoryManager;
    private ProcessControlBlock runningProcess;
    private int nextPID = 1;

    public ProcessManager(Kernel kernel, MemoryManager memoryManager) {
        this.kernel = kernel;
        this.memoryManager = memoryManager;
    }

    public ProcessControlBlock create(String path) throws IOException, ProcessException {
        FilePath imageFile = new FilePath(path);
        return create(imageFile);
    }

    public synchronized ProcessControlBlock create(FilePath imageFile) throws IOException, ProcessException {
        if (!imageFile.exists() || !imageFile.isFile()) return null;
        int imageSize = imageFile.size();

        int address = memoryManager.alloc(imageSize);
        if (address < 0) {
            throw new ProcessException("There is not enough memory to allocate for the new process.");
        }

        try (InputStream inputStream = new InputStream(imageFile)) {
            int value, currentPos = address;
            while ((value = inputStream.read()) != -1) {
                memoryManager.write(currentPos++, (byte) value);
            }
        }

        ProcessControlBlock PCB = new ProcessControlBlock(nextPID++, address, imageFile);
        if (!memoryManager.addPCB(PCB)) {
            memoryManager.free(address);
            throw new ProcessException("There is not enough PCB spaces for the new process.");
        }
        getReadyQueue().add(PCB);
        return PCB;
    }

    public synchronized void destroy(ProcessControlBlock PCB) {
        if (PCB.equals(runningProcess)) {
            assert PCB.getState() == ProcessState.RUNNING;
            runningProcess = null;
        } else if (!readyQueue.remove(PCB)) {
            // FIXME: stop IO?
            // blockedQueue.remove(PCB);
            throw new RuntimeException("There is no such PCB in readyQueue.");
        }
        memoryManager.free(PCB.getStartAddress());
        if (!memoryManager.removePCB(PCB)) {
            throw new RuntimeException("There is no such PCB.");
        }
        schedule();
    }

    public synchronized void block(ProcessControlBlock PCB) {
        if (PCB.equals(runningProcess)) {
            assert PCB.getState() == ProcessState.RUNNING;
            PCB.setState(ProcessState.BLOCKED);
            PCB.saveContext(kernel.getContext());
            getBlockedQueue().add(PCB);
            runningProcess = null;
            schedule();
        } else if (PCB.getState() == ProcessState.READY) {
            getReadyQueue().remove(PCB);
            PCB.setState(ProcessState.BLOCKED);
            getBlockedQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    public synchronized void awake(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessState.BLOCKED) {
            getBlockedQueue().remove(PCB);
            PCB.setState(ProcessState.READY);
            getReadyQueue().add(PCB);
        } else {
            throw new RuntimeException("Wrong PCB state");
        }
    }

    public synchronized void schedule() {
        if (runningProcess != null) {
            runningProcess.saveContext(kernel.getContext());
            runningProcess.setState(ProcessState.READY);
            readyQueue.add(runningProcess);
        }
        runningProcess = readyQueue.poll();
        kernel.resetTimeSlice();
        if (runningProcess != null) {
            runningProcess.setState(ProcessState.RUNNING);
            kernel.setContext(runningProcess.getContext());
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

    public synchronized List<Snapshot> snap() {
        List<Snapshot> snapshots = new LinkedList<>();
        if (runningProcess != null) {
            snapshots.add(new Snapshot(runningProcess));
        }
        for (ProcessControlBlock pcb : readyQueue) {
            snapshots.add(new Snapshot(pcb));
        }
        for (ProcessControlBlock pcb : blockedQueue) {
            snapshots.add(new Snapshot(pcb));
        }
        return snapshots;
    }

    public class Snapshot {
        private final int PID;
        private final String path;
        private final ProcessState status;
        private final int memorySize;
        private final Context context;

        public Snapshot(ProcessControlBlock pcb) {
            this(pcb.getPID(), pcb.getImageFile().getPath(), pcb.getState(),
                    memoryManager.getSpaceSize(pcb.getStartAddress()), pcb.getContext());
        }

        public Snapshot(int PID, String path, ProcessState status, int memorySize, Context context) {
            this.PID = PID;
            this.path = path;
            this.status = status;
            this.memorySize = memorySize;
            this.context = context;
        }

        public int getMemorySize() {
            return memorySize;
        }

        public int getPID() {
            return PID;
        }

        public String getPath() {
            return path;
        }

        public String getStatus() {
            return status.name();
        }

        public int getPC() {
            return context.getPC();
        }

    }

    public class ProcessException extends Exception {

        public ProcessException(String message) {
            super(message);
        }
    }
}
