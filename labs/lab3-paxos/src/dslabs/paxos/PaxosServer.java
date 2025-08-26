package dslabs.paxos;

import java.util.HashMap;
import java.util.Map;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PaxosServer extends Node {
    private final Address[] servers;
    private final Map<Integer, PaxosLogSlot> log = new HashMap<>();
    private final Map<Address, Integer> clearedUntil = new HashMap<>();

    public PaxosServer(Address address, Address[] servers, Application app) {
        super(address);
        this.servers = servers;
        for (Address server : servers) {
            clearedUntil.put(server, 0);
        }
    }

    @Override
    public void init() {
        set(new Timers(), Timers.HEARTBEAT_MILLIS);
    }

    public Command command(int logSlot) {
        PaxosLogSlot slot = log.get(logSlot);
        if (slot != null && slot.isChosen()) {
            return slot.getAcceptedCommand();
        }
        return null;
    }

    private synchronized void handlePrepare(Messages.Prepare m, Address sender) {
        PaxosLogSlot slot = log.computeIfAbsent(m.logSlot(), PaxosLogSlot::new);
        if (m.ballotNumber() >= slot.getBallot()) {
            slot.setBallot(m.ballotNumber());
            send(new Messages.Promise(m.ballotNumber(), slot.getBallot(), slot.getAcceptedCommand(), 0), sender);
        }
    }

    private synchronized void handlePromise(Messages.Promise m, Address sender) {
        PaxosLogSlot slot = log.computeIfAbsent(m.logSlot(), PaxosLogSlot::new);
        slot.recordPromise(sender, m.ballotNumber(), m.acceptedCommand());
    
        if (slot.hasMajorityPromises(servers.length)) {
            broadcastAccept(m.logSlot(), slot.getBallot(), slot.getCommandToPropose());
        }
    }    

    private synchronized void handleAccept(Messages.Accept m, Address sender) {
        PaxosLogSlot slot = log.computeIfAbsent(m.logSlot(), PaxosLogSlot::new);
        if (m.ballotNumber() >= slot.getBallot()) {
            slot.setBallot(m.ballotNumber());
            slot.accept(m.command());
            send(new Messages.Accepted(m.ballotNumber(), m.logSlot(), m.command()), sender);
        }
    }

    private synchronized void handleDecision(Messages.Decision m, Address sender) {
        PaxosLogSlot slot = log.computeIfAbsent(m.logSlot(), PaxosLogSlot::new);
        slot.decide(m.command());
        clearedUntil.put(address(), m.logSlot());
        performGarbageCollection();
    }

    private void broadcastAccept(int logSlot, int ballot, Command command) {
        for (Address server : servers) {
            send(new Messages.Accept(ballot, logSlot, command), server);
        }
    }

    public void onMessage(Message m, Address sender) {
        if (m instanceof Messages.Prepare) {
            handlePrepare((Messages.Prepare) m, sender);
        } else if (m instanceof Messages.Promise) {
            handlePromise((Messages.Promise) m, sender);
        } else if (m instanceof Messages.Accept) {
            handleAccept((Messages.Accept) m, sender);
        } else if (m instanceof Messages.Decision) {
            handleDecision((Messages.Decision) m, sender);
        }
    }

    private void performGarbageCollection() {
        int minCleared = clearedUntil.values().stream().min(Integer::compare).orElse(0);
        for (int i = 1; i <= minCleared; i++) {
            log.remove(i);
        }
    }

    public PaxosLogSlotStatus status(int logSlot) {
        PaxosLogSlot slot = log.get(logSlot);
        if (slot == null) {
            return PaxosLogSlotStatus.EMPTY;
        }
        if (slot.isChosen()) {
            return PaxosLogSlotStatus.CHOSEN;
        }
        if (slot.getAcceptedCommand() != null) {
            return PaxosLogSlotStatus.ACCEPTED;
        }
        return PaxosLogSlotStatus.EMPTY;
    }

    public int firstNonCleared() {
        return log.keySet().stream()
                .filter(slot -> status(slot) != PaxosLogSlotStatus.CLEARED)
                .min(Integer::compare)
                .orElse(1);
    }

    public int lastNonEmpty() {
        return log.keySet().stream()
                .filter(slot -> status(slot) != PaxosLogSlotStatus.EMPTY)
                .max(Integer::compare)
                .orElse(0);
    }

    private static class PaxosLogSlot {
        private final int logSlot;
        private int ballot;
        private Command acceptedCommand;
        private int acceptedBallot;
        private boolean decided;
        private final Map<Address, Integer> promises = new HashMap<>();

        public PaxosLogSlot(int logSlot) {
            this.logSlot = logSlot;
            this.ballot = 0;
            this.acceptedBallot = 0;
            this.decided = false;
        }

        public int getLogSlot() {
            return logSlot;
        }

        public int getBallot() {
            return ballot;
        }

        public void setBallot(int ballot) {
            this.ballot = ballot;
        }

        public Command getAcceptedCommand() {
            return acceptedCommand;
        }

        public void accept(Command command) {
            this.acceptedCommand = command;
        }

        public void decide(Command command) {
            this.decided = true;
            this.acceptedCommand = command;
        }

        public void recordPromise(Address sender, int ballot, Command command) {
            promises.put(sender, ballot);
            if (command != null && ballot > acceptedBallot) {
                acceptedBallot = ballot;
                acceptedCommand = command;
            }
        }

        public boolean hasMajorityPromises(int totalServers) {
            return promises.size() > totalServers / 2;
        }

        public Command getCommandToPropose() {
            return acceptedCommand;
        }

        public boolean isChosen() {
            return decided;
        }
    }
}
