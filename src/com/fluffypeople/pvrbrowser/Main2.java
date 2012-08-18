/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.apache.log4j.Logger;
import org.teleal.cling.UpnpService;
import org.teleal.cling.UpnpServiceImpl;
import org.teleal.cling.model.message.header.STAllHeader;

/**
 *
 * @author Osric
 */
public class Main2 {

    private static final Logger log = Logger.getLogger(Main2.class);

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        final DeviceListWindow win = new DeviceListWindow();
        final UpnpService upnpService = new UpnpServiceImpl(win);

        win.setService(upnpService);

        upnpService.getControlPoint().search(new STAllHeader());

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                upnpService.shutdown();
            }
        });

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                win.createAndDisplayGUI();


            }
        });

    }
}
