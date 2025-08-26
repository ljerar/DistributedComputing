package dslabs.paxos;

import dslabs.framework.Command;
import dslabs.framework.Message;
import lombok.Data;

public final class Messages {
    @Data
    public static class Prepare implements Message {
        private final int ballotNumber;
        private final int logSlot;
    }

    @Data
    public static class Promise implements Message {
        private final int ballotNumber;
        private final int acceptedBallot;
        private final Command acceptedCommand;
        private final int logSlot;
    }

    @Data
    public static class Accept implements Message {
        private final int ballotNumber;
        private final int logSlot;
        private final Command command;
    }

    @Data
    public static class Accepted implements Message {
        private final int ballotNumber;
        private final int logSlot;
        private final Command command;
    }

    @Data
    public static class Decision implements Message {
        private final int logSlot;
        private final Command command;
    }

    @Data
    public static class Heartbeat implements Message {
        private final int clearedSlot;
    }
}
