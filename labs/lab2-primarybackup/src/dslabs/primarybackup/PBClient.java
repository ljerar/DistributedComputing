package dslabs.primarybackup;

import java.util.HashMap;
import java.util.Map;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBClient extends Node implements Client {
    private final Address viewServer;
    private View currentView;
    private Command pendingCommand;
    private Result pendingResult;
    private final int clientId;
    private int requestId;
    @SuppressWarnings("FieldMayBeFinal")
    private Map<Integer, Result> results;

    public PBClient(Address address, Address viewServer) {
        super(address);
        this.viewServer = viewServer;
        this.clientId = generateId();
        this.requestId = 0;
        this.results = new HashMap<>();
    }

    @Override
    public synchronized void init() {
        send(new GetView(), viewServer);
        set(new ClientTimer(clientId, requestId, null), ClientTimer.CLIENT_RETRY_MILLIS);
    }
    

    @Override
    public synchronized void sendCommand(Command command) {
        pendingCommand = command;
        pendingResult = null;
        requestId++;
        sendRequest();
    }

    private synchronized void sendRequest() {
        if (currentView != null && currentView.primary() != null) {
            Request request = new Request(pendingCommand, clientId, requestId);
            send(request, currentView.primary());
        }
    }

    @Override
    public synchronized boolean hasResult() {
        return pendingResult != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        while (pendingResult == null) {
            wait();
        }
        Result result = pendingResult;
        pendingResult = null;
        pendingCommand = null;
        return result;
    }

    @SuppressWarnings("unused")
    private synchronized void handleReply(Reply m, Address sender) {
        if (sender.equals(currentView.primary()) && 
            m.clientId() == clientId && 
            m.requestId() == requestId) {
            pendingResult = m.result();
            results.put(m.requestId(), m.result());
            notify();
        }
    }

    @SuppressWarnings("unused")
    private synchronized void handleViewReply(ViewReply m, Address sender) {
        if (sender.equals(viewServer)) {
            currentView = m.view();
            if (pendingCommand != null) {
                sendRequest();
            }
        }
    }

    @SuppressWarnings("unused")
    private synchronized void onClientTimer(ClientTimer t) {
        if (pendingCommand != null &&
            t.clientId() == clientId &&
            t.requestId() == requestId &&
            t.command() == pendingCommand) {
            send(new GetView(), viewServer);
            sendRequest();
        }
        set(new ClientTimer(clientId, requestId, (dslabs.primarybackup.Command) pendingCommand), ClientTimer.CLIENT_RETRY_MILLIS);
    }

    private int generateId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }
}