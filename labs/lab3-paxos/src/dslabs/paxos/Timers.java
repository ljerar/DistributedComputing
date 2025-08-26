package dslabs.paxos;

import dslabs.framework.Timer;
import lombok.Data;

@Data
public final class Timers implements Timer {
    public static final int HEARTBEAT_MILLIS = 1000;  // 1 second interval
}
