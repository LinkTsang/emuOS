/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Link
 */
public class MemoryManager {

    private final int USER_SPACE_SIZE;
    private final AllocationMethod allocationMethod;
    private final LinkedList<Space> allocatedSpaces = new LinkedList<>();
    private final LinkedList<Space> freeSpaces = new LinkedList<>();
    private final ProcessControlBlock PCBList[] = new ProcessControlBlock[10];
    private byte userSpace[];

    public MemoryManager() {
        this(512);
    }

    public MemoryManager(int userSpaceSize) {
        this(AllocationMethod.FirstFit, userSpaceSize);
    }

    public MemoryManager(AllocationMethod allocationMethod, int userSpaceSize) {
        this.allocationMethod = allocationMethod;
        USER_SPACE_SIZE = userSpaceSize;
        userSpace = new byte[USER_SPACE_SIZE];
        freeSpaces.add(new Space(0, USER_SPACE_SIZE));
    }

    public int alloc(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than zero.");
        }
        switch (allocationMethod) {
            case FirstFit: {
                Iterator<Space> iterator = getFreeSpaces().iterator();
                while (iterator.hasNext()) {
                    Space space = iterator.next();
                    if (space.size >= size) {
                        Space newSpace = new Space(space.startAddress, size);
                        getAllocatedSpaces().add(newSpace);
                        space.startAddress += size;
                        space.size -= size;
                        if (space.size == 0) {
                            iterator.remove();
                        }
                        return newSpace.startAddress;
                    }
                }
            }
            break;
            default: {
                throw new UnsupportedOperationException();
            }
        }
        return -1;
    }

    public void free(int address) {
        if (address < 0) {
            throw new IllegalArgumentException(String.format("Illegal address, the address( = %d) must be greater than or equal to zero.", address));
        }
        Iterator<Space> allocatedSpaceIterator = getAllocatedSpaces().iterator();
        while (allocatedSpaceIterator.hasNext()) {
            Space allocatedSpace = allocatedSpaceIterator.next();
            if (address == allocatedSpace.startAddress) {
                ListIterator<Space> freeSpaceIterator = getFreeSpaces().listIterator();
                while (freeSpaceIterator.hasNext()) {
                    Space freeSpace = freeSpaceIterator.next();
                    if (freeSpace.startAddress + freeSpace.size == address) {
                        freeSpace.size += allocatedSpace.size;
                        if (freeSpaceIterator.hasNext()) {
                            Space nextfreeSpace = freeSpaceIterator.next();
                            if (freeSpace.startAddress + freeSpace.size == nextfreeSpace.startAddress) {
                                freeSpace.startAddress += nextfreeSpace.size;
                                freeSpaceIterator.remove();
                            }
                        }
                        allocatedSpaceIterator.remove();
                        return;
                    }
                    if (allocatedSpace.startAddress + allocatedSpace.size == freeSpace.startAddress) {
                        freeSpace.startAddress = allocatedSpace.startAddress;
                        freeSpace.size += allocatedSpace.size;
                        allocatedSpaceIterator.remove();
                        return;
                    }
                    if (allocatedSpace.startAddress + allocatedSpace.size < freeSpace.startAddress) {
                        freeSpaceIterator.previous();
                        freeSpaceIterator.add(allocatedSpace);
                        allocatedSpaceIterator.remove();
                        return;
                    }
                }
                freeSpaceIterator.add(allocatedSpace);
                allocatedSpaceIterator.remove();
                return;
            }
        }
        throw new IllegalArgumentException(String.format("Illegal address ( = %d ).", address));
    }

    public byte read(int address) {
        return userSpace[address];
    }

    public void write(int address, byte value) {
        userSpace[address] = value;
    }

    public int readInt(int address) {
        int value = 0;
        value |= userSpace[address];
        value |= userSpace[address + 1] << 8;
        value |= userSpace[address + 2] << 16;
        value |= userSpace[address + 3] << 24;
        return value;
    }

    public void writeInt(int address, int value) {
        userSpace[address] = (byte) value;
        userSpace[address + 1] = (byte) (value >>> 8);
        userSpace[address + 2] = (byte) (value >>> 16);
        userSpace[address + 3] = (byte) (value >>> 24);
    }

    public boolean isAllFree() {
        return allocatedSpaces.isEmpty();
    }

    /**
     * @return the allocatedSpaces
     */
    public List<Space> getAllocatedSpaces() {
        return allocatedSpaces;
    }

    /**
     * @return the freeSpaces
     */
    public List<Space> getFreeSpaces() {
        return freeSpaces;
    }

    /**
     * @return the USER_SPACE_SIZE
     */
    public int getMaxUserSpaceSize() {
        return USER_SPACE_SIZE;
    }

    /**
     * @return the PCBList
     */
    public ProcessControlBlock[] getPCBList() {
        return PCBList;
    }

    public boolean addPCB(ProcessControlBlock PCB) {
        for (int i = 0; i < PCBList.length; ++i) {
            if (PCBList[i] == null) {
                PCBList[i] = PCB;
                return true;
            }
        }
        return false;
    }

    public boolean removePCB(ProcessControlBlock PCB) {
        for (int i = 0; i < PCBList.length; ++i) {
            if (PCBList[i] == PCB) {
                PCBList[i] = null;
                return true;
            }
        }
        return false;
    }

    public enum AllocationMethod {
        FirstFit,
        NextFit,
        BestFit
    }

    public static class Space {

        public int startAddress;
        public int size;

        public Space(int startAddress, int size) {
            this.startAddress = startAddress;
            this.size = size;
        }
    }
}
