/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os;

import java.util.*;

/**
 * @author Link
 */
public class DeviceManager {
    // time in milliseconds between successive task executions.
    private static final int PERIOD = 1000;
    private static final int REST_TIME_INTERVAL = 1;
    private final Timer timer = new Timer(true);
    // DeviceID --> DeviceList --> Device Map
    private final Map<Integer, DeviceList> deviceListMap = new HashMap<>();
    private boolean running;
    private InterruptHandler finishedHandler;

    public DeviceManager() {
        DeviceList deviceListA = new DeviceList('A', 2);
        DeviceList deviceListB = new DeviceList('B', 3);
        DeviceList deviceListC = new DeviceList('C', 3);
        deviceListMap.put((int) 'A', deviceListA);
        deviceListMap.put((int) 'B', deviceListB);
        deviceListMap.put((int) 'C', deviceListC);
    }

    public InterruptHandler getFinishedHandler() {
        return finishedHandler;
    }

    public void setFinishedHandler(InterruptHandler handler) {
        this.finishedHandler = handler;
    }

    public void start() {
        synchronized (this) {
            if (!running) {
                timer.scheduleAtFixedRate(new DeviceTimerTask(), 0, PERIOD);
                running = true;
            }
        }
    }

    public void stop() {
        synchronized (this) {
            if (running) {
                timer.purge();
                running = false;
            }
        }
    }

    public synchronized void alloc(RequestInfo requestInfo) {
        DeviceList deviceList = deviceListMap.get(requestInfo.getDeviceType());
        if (deviceList != null) {
            deviceList.getWaitingQueue().add(requestInfo);
        }
    }

    public static interface InterruptHandler {
        void handler(DeviceInfo deviceInfo);
    }

    public static class DeviceInfo {
        private final int type;
        // rest time in milliseconds
        private int restTime;
        private ProcessControlBlock PCB;

        DeviceInfo(int type) {
            this.type = type;
            restTime = 0;
            PCB = null;
        }

        boolean isIdle() {
            return PCB == null;
        }

        void release() {
            PCB = null;
            restTime = 0;
        }

        void alloc(ProcessControlBlock user, int time) {
            this.PCB = user;
            this.restTime = time;
        }

        public ProcessControlBlock getPCB() {
            return PCB;
        }

        public int getType() {
            return type;
        }
    }

    public static class RequestInfo {
        private final ProcessControlBlock pcb;
        private final int deviceType;
        private final int time;

        public RequestInfo(ProcessControlBlock pcb, int deviceType, int time) {
            this.pcb = pcb;
            this.deviceType = deviceType;
            this.time = time;
        }

        public ProcessControlBlock getPCB() {
            return pcb;
        }

        public int getDeviceType() {
            return deviceType;
        }

        public int getTime() {
            return time;
        }
    }

    private static class DeviceList {
        final Queue<RequestInfo> waitingQueue;
        final List<DeviceInfo> deviceInfoList;

        DeviceList(int kind, int count) {
            deviceInfoList = new ArrayList<>();
            for (int i = 0; i < count; ++i) {
                deviceInfoList.add(new DeviceInfo(kind));
            }
            waitingQueue = new LinkedList<>();
        }

        List<DeviceInfo> getDeviceInfoList() {
            return deviceInfoList;
        }

        Queue<RequestInfo> getWaitingQueue() {
            return waitingQueue;
        }
    }

    private class DeviceTimerTask extends java.util.TimerTask {

        @Override
        public void run() {
            for (DeviceList list : deviceListMap.values()) {
                RequestInfo head = list.getWaitingQueue().peek();
                for (DeviceInfo deviceInfo : list.getDeviceInfoList()) {
                    if (!deviceInfo.isIdle()) {
                        deviceInfo.restTime -= REST_TIME_INTERVAL;
                        if (deviceInfo.restTime <= 0) {
                            if (finishedHandler != null) {
                                finishedHandler.handler(deviceInfo);
                            }
                            deviceInfo.release();
                        }
                    }
                    if (head != null && deviceInfo.isIdle()) {
                        head = list.getWaitingQueue().poll();
                        deviceInfo.alloc(head.getPCB(), head.getTime());
                        head = list.getWaitingQueue().peek();
                    }
                }
            }
        }
    }
}
