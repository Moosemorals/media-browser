/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.net.URI;
import java.util.Objects;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class RemoteItem {

    private static final Logger log = LoggerFactory.getLogger(RemoteItem.class);

    public static final double KILO = 1024;
    public static final double MEGA = KILO * 1024;
    public static final double GIGA = MEGA * 1024;
    public static final double TERA = GIGA * 1024;

    public static enum State {
        READY, DOWNLOADING, PAUSED, COMPLETED, ERROR
    };

    public enum Type {
        ITEM, CONTAINER
    };

    private final DIDLObject payload;
    private final Type type;
    private State state;
    private long size;
    private long downloaded;
    private String downloadPath;

    public RemoteItem(DIDLObject payload, Type type) {
        this.payload = payload;
        this.type = type;

        state = State.READY;
    }

    public RemoteItem(Item target) {
        this.payload = target;
        this.type = Type.ITEM;
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
        return (Item) payload;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setDownloaded(long downloaded) {
        this.downloaded = downloaded;
    }

    public Container getContainer() {
        return (Container) payload;
    }

    public void setDownloadPath(String path) {
        this.downloadPath = path;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public DIDLObject getPayload() {
        return payload;
    }

    public Type getType() {
        return type;
    }

    public String getUrl() {
        Res res = payload.getFirstResource();
        if (res != null) {
            URI uri = res.getImportUri();
            if (uri != null) {
                return uri.toASCIIString();
            } else {
                return "[No URI]";
            }
        } else {
            return "[No resource: " + payload.getClass() + "]";
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();

        result.append(payload.getTitle());
        result.append(": ");
        switch (state) {
            case READY:
                result.append(humanReadableSize(size)).append(" Queued");
                break;
            case DOWNLOADING:
                result.append(String.format("%s of %s (%3.0f%%) Downloading", humanReadableSize(downloaded), humanReadableSize(size), ((double) downloaded / (double) size) * 100.0));
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

        result.append(" (").append(downloadPath).append(")");

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
        final RemoteItem other = (RemoteItem) obj;
        return this.payload.equals(other.payload);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 11 * hash + Objects.hashCode(this.payload);
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
