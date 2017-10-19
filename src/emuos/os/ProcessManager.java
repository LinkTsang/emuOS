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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * @author Link
 */
public class ProcessManager {

    private final BlockingQueue<ProcessControlBlock> blockedQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<ProcessControlBlock> readyQueue = new LinkedBlockingQueue<>();
    private final Kernel kernel;
    private ProcessControlBlock runningProcess;
    private int nextPID = 1;

    ProcessManager(Kernel kernel, MemoryManager memoryManager) {
        this.kernel = kernel;
    }

    ProcessControlBlock create(String path) throws IOException, ProcessException {
        FilePath imageFile = new FilePath(path);
        return create(imageFile);
    }

    /**
     * create a process
     *
     * @param imageFile imageFile
     * @return PCB
     * @throws IOException      IOException
     * @throws ProcessException ProcessException
     */
    public synchronized ProcessControlBlock create(FilePath imageFile) throws IOException, ProcessException {
        if (!imageFile.exists() || !imageFile.isFile()) return null;
        int imageSize = imageFile.size();
        if (imageSize == 0) {
            throw new ProcessException("Create Process Failed: The image file \"" + imageFile.getPath() + "\" is empty.");
        }

        MemoryManager memoryManager = kernel.getMemoryManager();
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

    synchronized boolean destroy(ProcessControlBlock PCB) {
        Logger logger = Logger.getLogger(this.getClass().getName());
        MemoryManager memoryManager = kernel.getMemoryManager();
        if (PCB.equals(runningProcess)) {
            assert PCB.getState() == ProcessState.RUNNING;
            runningProcess = null;
        } else if (!readyQueue.remove(PCB)) {
            if (!blockedQueue.remove(PCB)) {
                logger.warning(PCB + " is not in the progress queue.");
                return false;
            }
            if (!kernel.getDeviceManager().detach(PCB)) {
                logger.warning(PCB + " is not in the device requiring queue.");
                return false;
            }
        }
        memoryManager.free(PCB.getStartAddress());
        if (!memoryManager.removePCB(PCB)) {
            logger.warning(PCB + " is not in the memory");
        }
        schedule();
        return true;
    }

    synchronized void block(ProcessControlBlock PCB) {
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
            Logger.getLogger(this.getClass().getName()).warning("Wrong PCB state: " + PCB);
        }
    }

    synchronized void awake(ProcessControlBlock PCB) {
        if (PCB.getState() == ProcessState.BLOCKED) {
            getBlockedQueue().remove(PCB);
            PCB.setState(ProcessState.READY);
            getReadyQueue().add(PCB);
        } else {
            Logger.getLogger(this.getClass().getName()).warning("Wrong PCB state: " + PCB);
        }
    }

    synchronized void schedule() {
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
    private BlockingQueue<ProcessControlBlock> getBlockedQueue() {
        return blockedQueue;
    }

    /**
     * @return the readyQueue
     */
    private BlockingQueue<ProcessControlBlock> getReadyQueue() {
        return readyQueue;
    }

    /**
     * @return the running process
     */
    public ProcessControlBlock getRunningProcess() {
        return runningProcess;
    }

    /**
     * @return the snapshot of processes
     */
    public synchronized List<Snapshot> snap() {
        MemoryManager memoryManager = kernel.getMemoryManager();
        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(Snapshot.IDLE);
        if (runningProcess != null) {
            snapshots.add(new Snapshot(memoryManager, runningProcess));
        }
        for (ProcessControlBlock pcb : readyQueue) {
            snapshots.add(new Snapshot(memoryManager, pcb));
        }
        for (ProcessControlBlock pcb : blockedQueue) {
            snapshots.add(new Snapshot(memoryManager, pcb));
        }
        return snapshots;
    }

    /**
     * Snapshot class
     */
    public static class Snapshot {
        static final Snapshot IDLE;

        static {
            ProcessControlBlock IDLE_PCB = ProcessControlBlock.IDLE;
            IDLE = new Snapshot(IDLE_PCB.getPID(),
                    IDLE_PCB.getImageFile().getPath(),
                    IDLE_PCB.getState(),
                    0,
                    IDLE_PCB.getContext());
        }

        private final int PID;
        private final String path;
        private final ProcessState status;
        private final int memorySize;
        private final Context context;

        private Snapshot(MemoryManager memoryManager, ProcessControlBlock pcb) {
            this(pcb.getPID(), pcb.getImageFile().getPath(), pcb.getState(),
                    memoryManager.getSpaceSize(pcb.getStartAddress()), pcb.getContext());
        }

        private Snapshot(int PID, String path, ProcessState status, int memorySize, Context context) {
            this.PID = PID;
            this.path = path;
            this.status = status;
            this.memorySize = memorySize;
            this.context = context;
        }

        /**
         * @return MemorySize
         */
        public int getMemorySize() {
            return memorySize;
        }

        /**
         * @return PID
         */
        public int getPID() {
            return PID;
        }

        /**
         * @return Path
         */
        public String getPath() {
            return path;
        }

        /**
         * @return Status
         */
        public String getStatus() {
            return status.name();
        }

        /**
         * @return PC
         */
        public int getPC() {
            return context.getPC();
        }

    }

    /**
     * ProcessException class
     */
    public class ProcessException extends Exception {
        ProcessException(String message) {
            super(message);
        }
    }
}
