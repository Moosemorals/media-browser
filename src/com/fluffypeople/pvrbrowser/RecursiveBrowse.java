/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.util.List;
import org.teleal.cling.UpnpService;
import org.teleal.cling.model.action.ActionInvocation;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.support.contentdirectory.callback.Browse;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.Res;
import org.teleal.cling.support.model.container.Container;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class RecursiveBrowse extends Browse {

    final Service service;
    final BrowseFlag flag;
    final UpnpService upnpService;
    final List<Item> allItems;

    public RecursiveBrowse(UpnpService upnpService, List<Item> allItems, Service service, String containerId, BrowseFlag flag) {
        super(service, containerId, flag);
        this.service = service;
        this.flag = flag;
        this.upnpService = upnpService;
        this.allItems = allItems;
    }

    @Override
    public void received(ActionInvocation actionInvocation, DIDLContent top) {
        List<Container> conts = top.getContainers();

        for (Container c : conts) {
            System.out.println("Container : " + c.getId() + c.getTitle());
            upnpService.getControlPoint().execute(new RecursiveBrowse(upnpService, allItems, service, c.getId(), flag));
        }
        List<Item> items = top.getItems();

        for (Item i : items) {
            allItems.add(i);
        }
    }

    @Override
    public void updateStatus(Status status) {
        // Noop
    }

    @Override
    public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
        // No-op
    }
}
