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
package com.fluffypeople.pvrbrowser;

import com.fluffypeople.pvrbrowser.PVR.PVRFile;
import com.fluffypeople.pvrbrowser.PVR.PVRFolder;
import com.fluffypeople.pvrbrowser.PVR.PVRItem;
import java.util.ArrayList;
import java.util.List;
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
public class UpnpRemote implements Runnable {

    private final Logger log = LoggerFactory.getLogger(UpnpRemote.class);

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";

    private final Object flag = new Object();
    private final UpnpService upnp;
    private final PVR pvr;
    private final List<DeviceBrowse> queue;
    private final AtomicBoolean running;

    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {
        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            if (DEVICE_NAME.equals(device.getDisplayString())) {
                log.debug("Found {}", DEVICE_NAME);
                populateTree(device);
            } else {
                log.info("Skiping device {} ", device.getDisplayString());
            }
        }
    };

    UpnpRemote(PVR pvr) {
        this.pvr = pvr;
        upnp = new UpnpServiceImpl(upnpListener);
        queue = new ArrayList<>();
        running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            upnp.getControlPoint().search(new STAllHeader());
            new Thread(this, "BrowseQueue").start();
        }
    }

    private void populateTree(RemoteDevice device) {
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        if (service != null) {
            synchronized (queue) {
                queue.add(new DeviceBrowse(service, "0\\1\\2", (PVRFolder) pvr.getRoot()));
                queue.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            DeviceBrowse next;

            try {
                synchronized (queue) {
                    while (queue.isEmpty()) {
                        queue.wait();
                    }
                    next = queue.remove(0);
                }

                upnp.getControlPoint().execute(next);
                synchronized (flag) {
                    flag.wait();
                }

                synchronized (queue) {
                    if (queue.isEmpty()) {
                        log.info("Browse complete");
                        running.set(false);
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                running.set(false);
                return;
            }
        }
        walkTree((PVRFolder) pvr.getRoot());
    }

    private void walkTree(PVRFolder folder) {
        for (PVRItem child : folder.getChildren()) {
            if (child.isFolder()) {
                log.debug("Folder : [{}] - [{}]", child.getPath(), child.getName());
                walkTree((PVRFolder) child);
            } else {
                log.debug("File   : [{}] - [{}] - [{}]", child.getPath(), child.getName(), ((PVRFile) child).getDownloadURL());
            }
        }
    }

    private class DeviceBrowse extends Browse {

        private final PVRFolder parent;
        private final Service service;

        public DeviceBrowse(Service service, String id, PVRFolder parent) {
            super(service, id, BrowseFlag.DIRECT_CHILDREN);
            this.parent = parent;
            this.service = service;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            List<Container> containers = didl.getContainers();

            for (Container c : containers) {
                PVRFolder folder = pvr.addFolder(parent, c.getTitle());
                synchronized (queue) {
                    queue.add(new DeviceBrowse(service, c.getId(), folder));
                    queue.notifyAll();
                }
            }
            List<Item> items = didl.getItems();

            for (Item i : items) {
                PVRFile file = pvr.addFile(parent, i.getTitle());

                Res res = i.getFirstResource();
                if (res != null) {
                    file.setDownloadURL(res.getValue());
                }

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

        }
    }
}
