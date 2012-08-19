/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.log4j.Logger;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class DownloadQueueItem {
    private static final Logger log = Logger.getLogger(DownloadQueueItem.class);

    public static enum State {READY, DOWNLOADING, PAUSED, COMPLETED, ERROR};

    private final List<StateChangeListener> listeners = new ArrayList<>();

    private final Item target;
    private State state;

    public DownloadQueueItem(Item target) {
        this.target = target;
        state = State.READY;
    }

    public void addStateChangeListener(StateChangeListener l) {
        listeners.add(l);
    }

    public void setState(State newState) {
        log.debug("State changed to " + newState);
        this.state = newState;
        notifyListeners();
    }

    public State getState() {
        return state;
    }

    public Item getTarget() {
        return target;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(target.getTitle());
        result.append(": ");
        switch (state) {
            case READY:
                result.append("Queued");
                break;
            case DOWNLOADING:
                result.append("Downloading");
                break;
            case PAUSED:
                result.append("Paused");
                break;
            case COMPLETED:
                result.append("Done");
                break;
            case ERROR:
                result.append("Broken");
                break;
        }

        return result.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DownloadQueueItem other = (DownloadQueueItem) obj;
        if (!Objects.equals(this.target, other.target)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.target);
        return hash;
    }

    private void notifyListeners() {
        for (StateChangeListener l : listeners) {
            l.stateChanged(this);
        }
    }

}
