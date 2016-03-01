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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
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
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class UpnpRemote {

    private final Logger log = LoggerFactory.getLogger(UpnpRemote.class);

    private static final String DEVICE_NAME = "HUMAX HDR-FOX T2 Undefine";

    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final UpnpService upnp;
    private final UI parent;
    private final DefaultRegistryListener upnpListener = new DefaultRegistryListener() {

        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            if (DEVICE_NAME.equals(device.getDisplayString())) {
                //DefaultMutableTreeNode node = new DefaultMutableTreeNode(device.getDisplayString());
                //treeModel.insertNodeInto(node, rootNode, rootNode.getChildCount());
                populateTree(device, rootNode);
            } else {
                log.info("Skiping device {} ", device.getDisplayString());
            }
        }
    };

    UpnpRemote(UI parent) {
        this.parent = parent;
        rootNode = new DefaultMutableTreeNode("Devices");
        treeModel = new DefaultTreeModel(rootNode);
        upnp = new UpnpServiceImpl(upnpListener);
    }

    public void start() {
        parent.setStatus("Searching for media servers");
        upnp.getControlPoint().search(new STAllHeader());
    }

    public TreeModel getTreeModel() {
        return treeModel;
    }

    private void populateTree(RemoteDevice device, DefaultMutableTreeNode parentNode) {
        Service service = device.findService(new UDAServiceType("ContentDirectory"));
        if (service != null) {
            upnp.getControlPoint().execute(new DeviceBrowse(service, "0\\1\\2", parentNode));
        }
    }

    private class DeviceBrowse extends Browse {

        private final DefaultMutableTreeNode parent;
        private final Service service;

        public DeviceBrowse(Service service, String id, DefaultMutableTreeNode parent) {
            super(service, id, BrowseFlag.DIRECT_CHILDREN);
            this.parent = parent;
            this.service = service;
        }

        @Override
        public void received(ActionInvocation actionInvocation, DIDLContent didl) {
            List<Container> containers = didl.getContainers();
            Collections.sort(containers, new Comparator<Container>() {
                @Override
                public int compare(Container t, Container t1) {
                    return t.getTitle().compareTo(t1.getTitle());
                }
            });

            for (Container c : containers) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new RemoteItem(c, RemoteItem.Type.CONTAINER));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());

                upnp.getControlPoint().execute(new DeviceBrowse(service, c.getId(), childNode));
            }
            List<Item> items = didl.getItems();
            Collections.sort(items, new Comparator<Item>() {

                @Override
                public int compare(Item t, Item t1) {
                    return t.getTitle().compareTo(t1.getTitle());
                }

            });

            for (Item i : items) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(new RemoteItem(i, RemoteItem.Type.ITEM));
                treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
            }

            if (parent == rootNode) {
                expandTreeRoot();
            }
        }

        @Override
        public void updateStatus(Browse.Status status) {

        }

        @Override
        public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {

        }
    }

    private void expandTreeRoot() {
        parent.expandTreeRoot(rootNode);
    }

}
