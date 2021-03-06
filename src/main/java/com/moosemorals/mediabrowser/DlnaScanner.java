/*
 * The MIT License
 *
 * Copyright 2016 Osric Wilkinson <osric@fluffypeople.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.moosemorals.mediabrowser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.Res;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class DlnaScanner implements Runnable {

    //private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";
    private static final String DEVICE_NAME = "HUMAX HDR-2000T Undefine";

    private static final long PAGE_SIZE = 50;

    private final Logger log = LoggerFactory.getLogger(DlnaScanner.class);
    private final PVR pvr;
    private final UpnpService upnpService;
    private final AtomicBoolean running;
    private final List<DeviceBrowse> queue;
    private final Object flag = new Object();
    private final DefaultRegistryListener registryListener = new DefaultRegistryListener() {
        private RemoteDevice connectedDevice = null;

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            if (DEVICE_NAME.equals(device.getDisplayString())) {
                log.info("Connected to {}", DEVICE_NAME);
                onConnect(device);
                connectedDevice = device;
            }
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            if (device.equals(connectedDevice)) {
                log.info("Disconnected from {}", DEVICE_NAME);
                onDisconnect(device);
                connectedDevice = null;
            }
        }
    };
    private Thread dlnaThread = null;
    private String remoteHostname = null;
    private Service service = null;

    public DlnaScanner(PVR pvr) {
        this.pvr = pvr;
        upnpService = new UpnpServiceImpl(registryListener);
        queue = new ArrayList<>();
        running = new AtomicBoolean(false);
    }

    void startSearch() {
        upnpService.getControlPoint().search(new STAllHeader());
    }

    void startScan() {
        if (service != null && running.compareAndSet(false, true)) {

            dlnaThread = new Thread(this, "DLNA");
            dlnaThread.start();
            notifyBrowseListeners(DeviceListener.ScanType.dlna, true);
            synchronized (queue) {
                queue.add(new DeviceBrowse(service, "0\\1\\2", ((PVRFolder) pvr.getRoot()), 0));
                queue.notifyAll();
            }
        } else if (service == null) {
            log.warn("Can't start scanning DLNA, no service");
        }
    }

    @Override
    public void run() {
        DeviceBrowse next;
        try {
            while (running.get()) {
                synchronized (queue) {
                    while (queue.isEmpty()) {
                        queue.wait();
                    }

                    next = queue.remove(0);
                }
                upnpService.getControlPoint().execute(next);
                synchronized (flag) {
                    flag.wait();
                }
                synchronized (queue) {
                    if (queue.isEmpty()) {
                        log.info("Browse complete");
                        return;
                    }
                }
            }
        } catch (InterruptedException ex) {
            log.error("IOException in DLNA thread: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
            notifyBrowseListeners(DeviceListener.ScanType.dlna, false);
        }
    }

    void stop() {
        if (running.compareAndSet(true, false) && dlnaThread != null) {
            dlnaThread.interrupt();
            log.info("Waiting for dlnaThread to finish");
            try {
                dlnaThread.join();
            } catch (InterruptedException ex) {
                log.error("Unexpected interruption waiting for dlnaThread, ignored");
            }
        }
    }

    public String getRemoteHostname() {
        return remoteHostname;
    }

    private void onConnect(RemoteDevice device) {
        remoteHostname = device.getIdentity().getDescriptorURL().getHost();

        service = device.findService(new UDAServiceType("ContentDirectory"));

        notifyConnectionListeners(true);
    }

    private void onDisconnect(RemoteDevice device) {
        notifyConnectionListeners(false);
    }

    /**
     * Part of the Cling framework. This class implements the dlna device search
     * stuff.
     */
    private class DeviceBrowse extends Browse {

        private final PVRFolder parent;
        private final Service service;
        private final String id;
        private final int page;

        DeviceBrowse(Service service, String id, PVRFolder parent, int page) {
            super(service, id, BrowseFlag.DIRECT_CHILDREN, CAPS_WILDCARD, page * PAGE_SIZE, PAGE_SIZE);
            this.id = id;
            this.page = page;
            this.parent = parent;
            this.service = service;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            List<Container> containers = didl.getContainers();

            for (Container c : containers) {
                PVRFolder folder = pvr.addFolder(parent, c.getTitle());
                folder.setDlnaScanned(true);
                pvr.updateItem(folder);
                synchronized (queue) {
                    queue.add(new DeviceBrowse(service, c.getId(), folder, 0));
                    queue.notifyAll();
                }

            }
            List<Item> items = didl.getItems();

            for (Item i : items) {
                PVRFile file = pvr.addFile(parent, i.getTitle());

                file.setDlnaScanned(true);

                Res res = i.getFirstResource();
                if (res != null) {
                    file.setRemoteURL(res.getValue());
                }
                pvr.updateItem(file);
            }

            if (items.size() + containers.size() == PAGE_SIZE) {
                // Get next page
                queue.add(new DeviceBrowse(service, id, parent, page + 1));
            }

            synchronized (flag) {
                flag.notifyAll();
            }
        }

        @Override
        public void updateStatus(Browse.Status status) {

        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
            log.error("Scan failure of some type");
            synchronized (flag) {
                flag.notifyAll();
            }
        }
    }

    private final Set<DeviceListener> deviceListener = new HashSet<>();

    public void addDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.add(l);
        }
    }

    public void removeDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.remove(l);
        }
    }

    private void notifyBrowseListeners(DeviceListener.ScanType type, boolean startStop) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (startStop) {
                    l.onScanStart(type);
                } else {
                    l.onScanComplete(type);
                }
            }
        }
    }

    private void notifyConnectionListeners(boolean connect) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (connect) {
                    l.onDeviceFound();
                } else {
                    l.onDeviceLost();
                }
            }
        }
    }

}
