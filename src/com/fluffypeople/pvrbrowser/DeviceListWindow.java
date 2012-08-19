/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.log4j.Logger;
import org.teleal.cling.UpnpService;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.Registry;

/**
 *
 * @author Osric
 */
public class DeviceListWindow extends DefaultRegistryListener implements ListSelectionListener, ActionListener {

    private static final Logger log = Logger.getLogger(DeviceListWindow.class);

    private JFrame frame;
    private final JList list;
    private final JButton openButton;
    private final BrowserListModel listModel;
    private final String openString = "Open Device";
    private final String chooseString = "Choose Download Folder";
    private UpnpService service;

    public DeviceListWindow() {

        listModel = new BrowserListModel();

        list = new JList(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.addListSelectionListener(this);
        list.setVisibleRowCount(-1);

        openButton = new JButton(openString);
        openButton.setActionCommand(openString);
        openButton.addActionListener(this);
        openButton.setEnabled(false);
    }

    public void setService(UpnpService service) {
        this.service = service;
    }

    public void createAndDisplayGUI() {
        frame = new JFrame("PVR Browser");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container cp = frame.getContentPane();
        cp.setLayout(new BorderLayout());

        JScrollPane listScrollPane = new JScrollPane(list);

        cp.add(listScrollPane, BorderLayout.CENTER);

        JButton chooseButton = new JButton(chooseString);
        chooseButton.setActionCommand(chooseString);
        chooseButton.addActionListener(this);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(chooseButton);
        buttonPanel.add(openButton);

        cp.add(buttonPanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setSize(400, 600);
        frame.setVisible(true);
    }

    @Override
    public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
        listModel.add(new DeviceListEntry(device));
    }

    @Override
    public void valueChanged(ListSelectionEvent lse) {
        openButton.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();

        if (cmd == null) {
            return;
        } else if (cmd.equals(openString)) {

            DeviceListEntry e = listModel.get(list.getSelectedIndex());
//            DeviceBrowserWindow win = new DeviceBrowserWindow(service, e.getDevice());

        } else if (cmd.equals(chooseString)) {

            final JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int returnVal = fc.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                DownloadForm.setDownloadFolder(fc.getSelectedFile());
            }
        }
    }
}
