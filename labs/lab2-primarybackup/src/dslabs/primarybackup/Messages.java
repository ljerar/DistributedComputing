package dslabs.primarybackup;

import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

/* -----------------------------------------------------------------------------------------------
 *  ViewServer Messages
 * ---------------------------------------------------------------------------------------------*/
@Data
class Ping implements Message {
    private final int viewNum;
}

@Data
class GetView implements Message {}

@Data
class ViewReply implements Message {
    private final View view;
}

/* -----------------------------------------------------------------------------------------------
 *  Primary-Backup Messages
 * ---------------------------------------------------------------------------------------------*/
@Data
class Request implements Message {
    private final Command command;
    private final int clientId;
    private final int requestId;
}

@Data
class Reply implements Message {
    private final Result result;
    private final int clientId;
    private final int requestId;
}

// Forward message from primary to backup
@Data
class Forward implements Message {
    private final Command command;
    private final int clientId;
    private final int requestId;
}

// Response from backup to primary
@Data
class ForwardReply implements Message {
    private final Result result;
    private final int clientId;
    private final int requestId;
    public Command command() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'command'");
    }
}

// State transfer message from primary to backup
@Data
class StateTransfer implements Message {
    private final Application state;
}

// Acknowledgment of state transfer from backup to primary
@Data
class StateAck implements Message {}