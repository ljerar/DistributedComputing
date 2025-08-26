package dslabs.primarybackup;

import java.util.HashMap;
import java.util.Map;

import dslabs.atmostonce.AMOApplication;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Node;
import dslabs.framework.Result;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PBServer extends Node {
    private final Address viewServer;
    private Application app;
    private final Application originalApp;
    
    private View currentView;
    private boolean isPrimary;
    private boolean isBackup;
    
    @SuppressWarnings("FieldMayBeFinal")
    private Map<Integer, Map<Integer, Result>> results; // clientId -> requestId -> result
    private boolean hasState;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    PBServer(Address address, Address viewServer, Application app) {
        super(address);
        this.viewServer = viewServer;
        this.originalApp = app;
        this.app = new AMOApplication(app);
        this.results = new HashMap<>();
        this.currentView = new View(ViewServer.STARTUP_VIEWNUM, null, null);
        this.hasState = false;
    }

    @Override
    public void init() {
        send(new Ping(ViewServer.STARTUP_VIEWNUM), viewServer);
        set(new PingTimer(), PingTimer.PING_MILLIS);
    }

    @SuppressWarnings("unused")
    private synchronized void handleRequest(Request m, Address sender) {
        if (!isPrimary) {
            return;
        }

        // Check if we've already processed this request
        Result result = getResult(m.clientId(), m.requestId());
                if (result != null) {
                    send(new Reply(result, m.clientId(), m.requestId()), sender);
                    return;
                }
        
                // If we have a backup, forward the request
                if (currentView.backup() != null) {
                    Forward fwd = new Forward(m.command(), m.clientId(), m.requestId());
                    send(fwd, currentView.backup());
                    return;
                }
        
                // No backup - execute directly
                result = app.execute(m.command());
                recordResult(m.clientId(), m.requestId(), result);
                send(new Reply(result, m.clientId(), m.requestId()), sender);
            }
        
            private Result getResult(@SuppressWarnings("unused") int clientId, @SuppressWarnings("unused") int requestId) {
                // TODO Auto-generated method stub
                throw new UnsupportedOperationException("Unimplemented method 'getResult'");
            }
        
            @SuppressWarnings("unused")
    private synchronized void handleForward(Forward m, Address sender) {
        if (!isBackup || !sender.equals(currentView.primary())) {
            return;
        }

        Result result = app.execute(m.command());
        send(new ForwardReply(result, m.clientId(), m.requestId()), sender);
    }

    @SuppressWarnings("unused")
    private synchronized void handleForwardReply(ForwardReply m, Address sender) {
        if (!isPrimary || !sender.equals(currentView.backup())) {
            return;
        }

        Result result = app.execute(m.command());
        recordResult(m.clientId(), m.requestId(), result);
        send(new Reply(result, m.clientId(), m.requestId()), sender);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "unused" })
    private synchronized void handleViewReply(ViewReply m, Address sender) {
        if (!sender.equals(viewServer)) {
            return;
        }

        View newView = m.view();
        
        // If we're becoming primary and we were backup, we're ready
        if (address.equals(newView.primary()) && address.equals(currentView.backup())) {
            hasState = true;
        }
        
        // If we're becoming primary but weren't in the previous view, wait for state
        if (address.equals(newView.primary()) && 
            !address.equals(currentView.primary()) &&
            !address.equals(currentView.backup())) {
            return;
        }

        // Update our role
        boolean wasPrimary = isPrimary;
        currentView = newView;
        isPrimary = address.equals(newView.primary());
        isBackup = address.equals(newView.backup());

        // If we're the new backup and weren't previously in the view, we need state
        if (isBackup && !wasPrimary && !hasState) {
            app = new AMOApplication(originalApp);
            results.clear();
            hasState = false;
            send(new StateTransfer(app), currentView.primary());
        }
    }

    @SuppressWarnings("unused")
    private synchronized void handleStateTransfer(StateTransfer m, Address sender) {
        if (!isBackup || !sender.equals(currentView.primary())) {
        } else {
            this.app = m.state();
            hasState = true;
            send(new StateAck(), sender);
        }
    }

    @SuppressWarnings("unused")
    private synchronized void handleStateAck(StateAck m, Address sender) {
        if (isPrimary && sender.equals(currentView.backup())) {
            hasState = true;
        }
    }

    @SuppressWarnings("unused")
    private void onPingTimer(PingTimer t) {
      send(new Ping(currentView.viewNum()), viewServer);
      set(t, PingTimer.PING_MILLIS);
  }

  private void recordResult(int clientId, int requestId, Result result) {
      results.computeIfAbsent(clientId, k -> new HashMap<>())
             .put(requestId, result);
  }
}