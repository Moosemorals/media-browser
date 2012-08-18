package com.fluffypeople.pvrbrowser;

import java.util.ArrayList;
import java.util.List;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.controlpoint.SubscriptionCallback;
import org.teleal.cling.model.gena.CancelReason;
import org.teleal.cling.model.gena.GENASubscription;
import org.teleal.cling.model.message.UpnpResponse;
import org.teleal.cling.model.message.header.STAllHeader;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.types.UDAServiceId;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import org.teleal.cling.support.model.BrowseFlag;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class Main {

    static private void indent(int depth) {
        for (int i = 0; i < depth; i++) {
            System.out.print(" ");
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        // This will create necessary network resources for UPnP right away
        System.out.println("Starting Cling...");
        final UpnpService upnpService = new UpnpServiceImpl();

        final List<Item> allItems = new ArrayList<Item>();

        // SubscriptionCallback callback =

        // UPnP discovery is asynchronous, we need a callback
        RegistryListener listener = new DefaultRegistryListener() {

            @Override
            public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
                System.out.println("Got device " + device.getDisplayString());

                Service service = device.findService(new UDAServiceId("ContentDirectory"));
                if (service != null) {
                    System.out.println(service.getServiceId().getId());
             //       upnpService.getControlPoint().execute(new RecursiveBrowse(upnpService, allItems, service, "0", BrowseFlag.DIRECT_CHILDREN));

                } else {
                    System.out.println("No services");
                }

                /*
                 * Service service = device.findService(new UDAServiceId("AVTransport")); if (service != null) {
                 * System.out.println(service.getServiceId().getId()); upnpService.getControlPoint().execute(new
                 * SubscriptionCallback(service, 600) {
                 *
                 * @Override public void established(GENASubscription sub) { System.out.println("Established: " +
                 * sub.getSubscriptionId()); }
                 *
                 * @Override protected void failed(GENASubscription sub, UpnpResponse status, Exception ex, String msg) {
                 * System.out.println("Faild: " + msg); }
                 *
                 * @Override public void ended(GENASubscription sub, CancelReason reason, UpnpResponse response) {
                 * System.out.println("Ended "); }
                 *
                 * @Override public void eventsMissed(GENASubscription sub, int num) { System.out.println("Missed events :" +
                 * num); }
                 *
                 * @Override protected void eventReceived(GENASubscription sub) { System.out.println("Event: " +
                 * sub.getCurrentSequence().getValue()); } });
                 *
                 * } else { System.out.println("No services"); }
                 *
                 *
                 * // Service service = device.findService(new UDAServiceId("ContentDirectory"));
                *
                 */
            }

            @Override
            public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
                System.out.println(
                        "Remote device updated: " + device.getDisplayString());
            }

            @Override
            public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
                System.out.println(
                        "Remote device removed: " + device.getDisplayString());
            }
        };

        upnpService.getRegistry().addListener(listener);


        //  UDN udn = new UDN("e495bf1d-ac96-4452-a913-DCD32155F321");

        // Send a search message to all devices and services, they should respond soon
        upnpService.getControlPoint().search(new STAllHeader());

        //  upnpService.getControlPoint().execute(callback);

        // Let's wait 10 seconds for them to respond
        System.out.println("Waiting 10 seconds before shutting down...");
        Thread.sleep(10000);

        // Release all resources and advertise BYEBYE to other UPnP devices
        System.out.println("Stopping Cling...");
        upnpService.shutdown();


        for (Item i : allItems) {
            System.out.println(i.getTitle() + ": " + i.getFirstResource().getValue());
        }
    }
}
