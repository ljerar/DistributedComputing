package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Message;
import lombok.Data;

@Data
public final class PaxosRequest implements Command, Message {
    private final Command command;
    private final int clientSeqNum;
    private final Address clientAddress;
}