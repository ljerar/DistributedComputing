package dslabs.primarybackup;

import java.util.HashMap;
import java.util.Map;

import dslabs.framework.Address;
import dslabs.framework.Node;
import static dslabs.primarybackup.PingCheckTimer.PING_CHECK_MILLIS;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class ViewServer extends Node {
    static final int STARTUP_VIEWNUM = 0;
    private static final int INITIAL_VIEWNUM = 1;
    
    // Current view
    private View currentView;
    
    // Track the last view number acknowledged by the primary
    private int lastAckedViewNum;
    
    // Track server pings
    private Map<Address, Boolean> currentPings;
    private Map<Address, Boolean> previousPings;
    
    // Track the latest view number each server has reported
    @SuppressWarnings("FieldMayBeFinal")
    private Map<Address, Integer> serverViewNums;

    public ViewServer(Address address) {
        super(address);
        this.currentView = new View(STARTUP_VIEWNUM, null, null);
        this.lastAckedViewNum = STARTUP_VIEWNUM;
        this.currentPings = new HashMap<>();
        this.previousPings = new HashMap<>();
        this.serverViewNums = new HashMap<>();
    }

    @Override
    public void init() {
        set(new PingCheckTimer(), PING_CHECK_MILLIS);
    }

    @SuppressWarnings("unused")
    private void handlePing(Ping m, Address sender) {
        // Record the ping
        currentPings.put(sender, true);
        
        // Track the server's view number
        serverViewNums.put(sender, m.viewNum());
        
        // If this is the primary acknowledging the current view
        if (sender.equals(currentView.primary()) && 
            m.viewNum() == currentView.viewNum()) {
            lastAckedViewNum = currentView.viewNum();
        }
        
        // Try to start the first view if we haven't yet
        if (currentView.viewNum() == STARTUP_VIEWNUM && 
            currentView.primary() == null) {
            currentView = new View(INITIAL_VIEWNUM, sender, null);
        }
        
        // Check if we need to create a new view
        tryCreateNewView();
        
        // Send current view back
        send(new ViewReply(currentView), sender);
    }

    @SuppressWarnings("unused")
    private void handleGetView(GetView m, Address sender) {
        send(new ViewReply(currentView), sender);
    }

    @SuppressWarnings("unused")
    private void onPingCheckTimer(PingCheckTimer t) {
        // Move current pings to previous pings and clear current
        previousPings = currentPings;
        currentPings = new HashMap<>();
        
        // Try to create a new view based on server status
        tryCreateNewView();
        
        // Restart timer
        set(t, PING_CHECK_MILLIS);
    }

    private void tryCreateNewView() {
        // Can't create new view until primary acks current one
        if (currentView.viewNum() != lastAckedViewNum) {
            return;
        }

        Address primary = currentView.primary();
        Address backup = currentView.backup();
        
        // Check if primary is dead
        if (primary != null && !isAlive(primary)) {
            if (backup != null) {
                // Promote backup to primary
                currentView = new View(currentView.viewNum() + 1, backup, findNewBackup(backup));
            }
            return;
        }
        
        // Check if backup is dead or missing
        if ((backup != null && !isAlive(backup)) || 
            (backup == null && primary != null)) {
            Address newBackup = findNewBackup(primary);
            if (newBackup != null) {
                currentView = new View(currentView.viewNum() + 1, primary, newBackup);
            }
        }
    }

    private boolean isAlive(Address server) {
        return currentPings.getOrDefault(server, false) || 
               previousPings.getOrDefault(server, false);
    }

    private Address findNewBackup(Address primary) {
        // Find a live server that isn't the primary
        for (Address server : currentPings.keySet()) {
            if (!server.equals(primary) && isAlive(server)) {
                return server;
            }
        }
        for (Address server : previousPings.keySet()) {
            if (!server.equals(primary) && isAlive(server)) {
                return server;
            }
        }
        return null;
    }
}