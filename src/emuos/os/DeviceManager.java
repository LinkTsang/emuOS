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
class DeviceManager {
    // time in milliseconds between successive task executions.
    private static final int PERIOD = 1000;
    private final Timer timer = new Timer(true);
    private final Map<Integer, DeviceList> deviceListMap = new HashMap<>();
    private boolean running;
    private FinishedHandler finishedHandler;

    DeviceManager() {
        DeviceList deviceListA = new DeviceList('A', 2);
        DeviceList deviceListB = new DeviceList('B', 3);
        DeviceList deviceListC = new DeviceList('C', 3);
        deviceListMap.put((int) 'A', deviceListA);
        deviceListMap.put((int) 'B', deviceListB);
        deviceListMap.put((int) 'C', deviceListC);
    }

    void setFinishedHandler(FinishedHandler handler) {
        this.finishedHandler = handler;
    }

    void start() {
        synchronized (this) {
            if (!running) {
                timer.scheduleAtFixedRate(new DeviceTimerTask(), 0, PERIOD);
                running = true;
            }
        }
    }

    void stop() {
        synchronized (this) {
            if (running) {
                timer.purge();
                running = false;
            }
        }
    }

    synchronized void alloc(RequestInfo requestInfo) {
        DeviceList deviceList = deviceListMap.get(requestInfo.getKind());
        if (deviceList != null) {
            deviceList.getWaitingQueue().add(requestInfo);
        }
    }

    static interface FinishedHandler {
        void handler(DeviceInfo deviceInfo);
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

    static class DeviceInfo {
        final int kind;
        // rest time in milliseconds
        int restTime;
        ProcessControlBlock user;

        DeviceInfo(int kind) {
            this.kind = kind;
            restTime = 0;
            user = null;
        }

        boolean isIdle() {
            return user == null;
        }

        void release() {
            user = null;
            restTime = 0;
        }

        void alloc(ProcessControlBlock user, int time) {
            this.user = user;
            this.restTime = time;
        }
    }

    static class RequestInfo {
        private final ProcessControlBlock pcb;
        private final int kind;
        private final int time;

        RequestInfo(ProcessControlBlock pcb, int kind, int time) {
            this.pcb = pcb;
            this.kind = kind;
            this.time = time;
        }

        ProcessControlBlock getPcb() {
            return pcb;
        }

        int getKind() {
            return kind;
        }

        int getTime() {
            return time;
        }
    }

    private class DeviceTimerTask extends java.util.TimerTask {

        @Override
        public void run() {
            for (DeviceList list : deviceListMap.values()) {
                RequestInfo head = list.getWaitingQueue().peek();
                for (DeviceInfo deviceInfo : list.getDeviceInfoList()) {
                    if (!deviceInfo.isIdle()) {
                        deviceInfo.restTime -= PERIOD;
                        if (deviceInfo.restTime <= 0) {
                            if (finishedHandler != null) {
                                finishedHandler.handler(deviceInfo);
                            }
                            deviceInfo.release();
                        }
                    }
                    if (head != null && deviceInfo.isIdle()) {
                        head = list.getWaitingQueue().poll();
                        deviceInfo.alloc(head.getPcb(), head.getTime());
                        head = list.getWaitingQueue().peek();
                    }
                }
            }
        }
    }
}
