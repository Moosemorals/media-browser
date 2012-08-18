/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import org.teleal.cling.model.meta.RemoteDevice;

/**
 *
 * @author Osric
 */
public class DeviceListEntry {

    private final RemoteDevice device;

    public DeviceListEntry(RemoteDevice device) {
        this.device = device;
    }

    @Override
    public String toString() {
        return device.getDisplayString();
    }

    @Override
    public int hashCode() {
        return device.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return device.equals(other);
    }

    public RemoteDevice getDevice() {
        return device;
    }
}
