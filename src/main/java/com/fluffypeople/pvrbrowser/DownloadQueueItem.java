/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class DownloadQueueItem {

    private static final Logger log = LoggerFactory.getLogger(DownloadQueueItem.class);

    public static enum State {
        READY, DOWNLOADING, PAUSED, COMPLETED, ERROR
    };

    private final List<StateChangeListener> stateListeners = new ArrayList<>();
    private final List<DownloadListener> downloadListeners = new ArrayList<>();

    private final Item target;
    private State state;
    private long size;
    private long downloaded;

    public DownloadQueueItem(Item target) {
        this.target = target;
        state = State.READY;
    }

    public void addStateChangeListener(StateChangeListener l) {
        stateListeners.add(l);
    }

    public void addDownloadListener(DownloadListener l) {
        downloadListeners.add(l);
    }

    public void setState(State newState) {
        log.debug("State changed to " + newState);
        this.state = newState;
        notifyStateListeners();
    }

    public State getState() {
        return state;
    }

    public Item getTarget() {
        return target;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setDownloaded(long downloaded) {
        this.downloaded = downloaded;
        notifyDownloadListners();
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
        return this.target.equals(other.target);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.target);
        return hash;
    }

    private void notifyStateListeners() {
        for (StateChangeListener l : stateListeners) {
            l.stateChanged(this);
        }
    }

    private void notifyDownloadListners() {
        for (DownloadListener l : downloadListeners) {
            l.updateDownload(size, downloaded);
        }
    }
}
