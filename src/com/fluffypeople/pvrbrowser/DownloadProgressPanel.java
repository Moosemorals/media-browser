/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.FlowLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 *
 * @author Osric
 */
public class DownloadProgressPanel extends JPanel {

    private final JPanel holder;

    public DownloadProgressPanel() {

        holder = new JPanel();

        holder.setLayout(new FlowLayout());

        JScrollPane jsp = new JScrollPane(holder);

        add(jsp);
    }


    public void add(DownloadQueueItem item) {
        DownloadProgressItem p = new DownloadProgressItem(item);
        holder.add(p);
        item.addStateChangeListener(p);
    }

}
