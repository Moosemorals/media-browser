/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/**
 *
 * @author Osric
 */
public class DownloadProgressItem extends JPanel implements StateChangeListener {

    private final JLabel filename;
    private final JProgressBar progress;
    private final DownloadQueueItem item;

    public DownloadProgressItem(DownloadQueueItem item) {
        this.item = item;

        filename = new JLabel();
        filename.setText(item.getTarget().getTitle());

        progress = new JProgressBar();
        progress.setStringPainted(true);

        setLayout(new BorderLayout());
        add(filename, BorderLayout.LINE_START);
        add(progress, BorderLayout.LINE_END);
    }

    public void updateProgress(int value) {
        progress.setValue(value);
    }

    @Override
    public void stateChanged(DownloadQueueItem source) {
        repaint();
    }
}
