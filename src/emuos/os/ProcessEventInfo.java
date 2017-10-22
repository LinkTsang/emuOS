package emuos.os;

/**
 * @author Link
 */
public class ProcessEventInfo {
    private long time;
    private String event;
    private ProcessControlBlock pcb;

    public ProcessEventInfo(long time, String event, ProcessControlBlock pcb) {
        this.time = time;
        this.event = event;
        this.pcb = pcb;
    }

    public long getTime() {
        return time;
    }

    public int getPID() {
        return pcb.getPID();
    }

    public String getType() {
        return event;
    }

    public ProcessControlBlock getPCB() {
        return pcb;
    }

    public int getAX() {
        return pcb.getContext().getAX();
    }

    public int getPC() {
        return pcb.getContext().getPC();
    }
}
