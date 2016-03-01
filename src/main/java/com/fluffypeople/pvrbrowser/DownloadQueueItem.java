/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

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

    public static final double KILO = 1024;
    public static final double MEGA = KILO * 1024;
    public static final double GIGA = MEGA * 1024;
    public static final double TERA = GIGA * 1024;

    public static enum State {
        READY, DOWNLOADING, PAUSED, COMPLETED, ERROR
    };

    private final Item target;
    private State state;
    private long size;
    private long downloaded;

    public DownloadQueueItem(Item target) {
        this.target = target;
        state = State.READY;
    }

    public void setState(State newState) {
        log.debug("State changed to " + newState);
        this.state = newState;
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
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(target.getTitle());
        result.append(": ");
        switch (state) {
            case READY:
                result.append(humanReadableSize(size)).append(" Queued");
                break;
            case DOWNLOADING:
                result.append(String.format("%s of %s (%3.0f%%) Dowloading", humanReadableSize(downloaded), humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
                break;
            case PAUSED:
                result.append(String.format("%s of %s (%3.0f%%) Paused", humanReadableSize(downloaded), humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
                break;
            case COMPLETED:
                result.append(humanReadableSize(downloaded)).append(" Completed");
                break;
            case ERROR:
                result.append(String.format("%s of %s (%3.0f%%) Broken", humanReadableSize(downloaded), humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
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

    public static String humanReadableSize(long size) {

        if (size > TERA) {
            return String.format("% 5.2fTb", (double) size / TERA);
        } else if (size > GIGA) {
            return String.format("% 5.2fGb", (double) size / GIGA);
        } else if (size > MEGA) {
            return String.format("% 5.2fMb", (double) size / MEGA);
        } else if (size > KILO) {
            return String.format("% 5.2fkb", (double) size / MEGA);
        } else {
            return String.format("% 5db", size);
        }

    }
}
