package dslabs.primarybackup;

import dslabs.framework.Timer;
import lombok.Data;

@Data
final class PingCheckTimer implements Timer {
    static final int PING_CHECK_MILLIS = 100;
}

@Data
final class PingTimer implements Timer {
    static final int PING_MILLIS = 25;
}

@Data
final class ClientTimer implements Timer {
    static final int CLIENT_RETRY_MILLIS = 100;
    private final int clientId;
    private final int requestId;
    private final Command command;  // Make sure Command is defined

    // You can add constructor if needed
    public ClientTimer(int clientId, int requestId, Command pendingCommand) {
        this.clientId = clientId;
        this.requestId = requestId;
        this.command = pendingCommand;
    }
}

// Assuming Command is a simple class
@Data
class Command {
    private String commandName; // Example field

    // Additional fields and methods
}

@Data
final class StateTransferTimer implements Timer {
    @SuppressWarnings("unused")
    static final int STATE_TRANSFER_MILLIS = 50;
}
