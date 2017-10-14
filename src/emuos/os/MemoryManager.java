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

    private static final int MAX_PCB_COUNT = 10;
    private static final int DEFAULT_USER_SPACE_SIZE = 512;
    private final int USER_SPACE_SIZE;
    private final LinkedList<Space> allocatedSpaces = new LinkedList<>();
    private final LinkedList<Space> freeSpaces = new LinkedList<>();
    private final ProcessControlBlock PCBList[] = new ProcessControlBlock[MAX_PCB_COUNT];
    private byte userSpace[];
    private Allocator allocator;

    public MemoryManager() {
        this(DEFAULT_USER_SPACE_SIZE);
    }

    private MemoryManager(int userSpaceSize) {
        this(AllocationMethod.FirstFit, userSpaceSize);
    }

    private MemoryManager(AllocationMethod allocationMethod, int userSpaceSize) {
        switch (allocationMethod) {
            case FirstFit:
                allocator = new FirstFitAllocator();
                break;
            default:
                throw new UnsupportedOperationException();
        }
        USER_SPACE_SIZE = userSpaceSize;
        userSpace = new byte[USER_SPACE_SIZE];
        freeSpaces.add(new Space(0, USER_SPACE_SIZE));
    }

    public int alloc(int size) {
        return allocator.alloc(size);
    }

    public void free(int address) {
        allocator.free(address);
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

    boolean addPCB(ProcessControlBlock PCB) {
        for (int i = 0; i < PCBList.length; ++i) {
            if (PCBList[i] == null) {
                PCBList[i] = PCB;
                return true;
            }
        }
        return false;
    }

    boolean removePCB(ProcessControlBlock PCB) {
        for (int i = 0; i < PCBList.length; ++i) {
            if (PCBList[i] == PCB) {
                PCBList[i] = null;
                return true;
            }
        }
        return false;
    }

    /**
     * @param address address
     * @return size
     */
    public int getSpaceSize(int address) {
        for (Space s : allocatedSpaces) {
            if (s.startAddress == address) {
                return s.size;
            }
        }
        return -1;
    }

    /**
     * AllocationMethod
     */
    public enum AllocationMethod {
        FirstFit,
        NextFit,
        BestFit
    }

    /**
     * Space class
     */
    public static class Space {

        public int startAddress;
        public int size;

        private Space(int startAddress, int size) {
            this.startAddress = startAddress;
            this.size = size;
        }
    }

    private abstract class Allocator {
        abstract int alloc(int size);

        void free(int address) {
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
                                Space nextFreeSpace = freeSpaceIterator.next();
                                if (freeSpace.startAddress + freeSpace.size == nextFreeSpace.startAddress) {
                                    freeSpace.startAddress += nextFreeSpace.size;
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
    }

    private class FirstFitAllocator extends Allocator {
        @Override
        int alloc(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("size must be greater than zero.");
            }
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
            return -1;
        }
    }

}
