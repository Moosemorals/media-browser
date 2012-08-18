/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractListModel;

/**
 *
 * @author Osric
 */
public class BrowserListModel extends AbstractListModel {

    private final List<DeviceListEntry> list;

    public BrowserListModel() {
        list = new ArrayList<>();
    }

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int i) {
        return list.get(i);
    }

    public void add(DeviceListEntry e) {
        list.add(e);
        fireIntervalAdded(this, 0, list.size());
    }

    public DeviceListEntry get(int i) {
        return list.get(i);
    }

    public void remove(DeviceListEntry e) {
        list.remove(e);
        fireIntervalRemoved(this, 0, list.size());
    }
}
