/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;


import java.net.URI;
import org.teleal.cling.support.model.DIDLObject;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class TreeItemHolder {

    public enum Type { ITEM, CONTAINER };


    private final DIDLObject payload;
    private final Type type;


    public TreeItemHolder(DIDLObject payload, Type type) {
        this.payload = payload;
        this.type = type;
    }

    public Item getItem() {
        return (Item)payload;
    }

    public Container getContainer() {
        return (Container)payload;
    }

    @Override
    public String toString() {
        return payload.getTitle();
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

}
