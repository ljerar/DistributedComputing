package dslabs.paxos;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class PaxosClient extends Node implements Client {
    private final Address[] servers;
    private Command pendingCommand;
    private Result commandResult;

    public PaxosClient(Address address, Address[] servers) {
        super(address);
        this.servers = servers;
    }

    @Override
    public synchronized void init() {
        // Initialize state if needed
    }

    @Override
    public synchronized void sendCommand(Command operation) {
        this.pendingCommand = operation;
        this.commandResult = null;  // Reset previous result
        broadcastRequest(new PaxosRequest(operation, 0, address));
    }

    @Override
    public synchronized boolean hasResult() {
        return commandResult != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        while (commandResult == null) {
            wait();
        }
        return commandResult;
    }

    private synchronized void handlePaxosReply(PaxosReply reply) {
        if (pendingCommand != null && pendingCommand.equals(reply.command())) {
            this.commandResult = reply.result();
            notifyAll();
        }
    }

    public void onMessage(Message message, Address sender) {
        if (message instanceof PaxosReply) {
            handlePaxosReply((PaxosReply) message);
        }
    }

    private void broadcastRequest(PaxosRequest request) {
        for (Address server : servers) {
            send(request, server);
        }
    }
}
