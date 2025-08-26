package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Result;
import lombok.Data;

@Data
public final class PaxosReply implements Message {
    private final Command command;
    private final Result result;
    private final int clientSeqNum;
    private final Address clientAddress;
}