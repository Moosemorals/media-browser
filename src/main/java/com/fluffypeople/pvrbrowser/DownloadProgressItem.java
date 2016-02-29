/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class DownloadProgressItem extends JPanel implements StateChangeListener, DownloadListener {

    private static final Logger log = LoggerFactory.getLogger(DownloadProgressItem.class);

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
        add(Box.createRigidArea(new Dimension(5, 0)));
        add(progress, BorderLayout.LINE_END);
    }

    @Override
    public void updateDownload(final long total, final long done) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (total > Integer.MAX_VALUE) {
                    log.debug("Shrinking total as {} bigger than {}", total, Integer.MAX_VALUE);
                    progress.setMaximum((int) (total / (1024 * 1024)));
                    progress.setValue((int) (done / (1024 * 1024)));
                } else {
                    progress.setMaximum((int) total);
                    progress.setValue((int) done);
                }
            }
        });

    }

    @Override
    public void stateChanged(DownloadQueueItem source) {
        repaint();
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension f = filename.getPreferredSize();
        Dimension p = progress.getPreferredSize();

        return new Dimension(f.width + p.width + 5, f.height > p.height ? f.height : p.height);
    }

    @Override
    public Dimension getPreferredSize() {
        return getMinimumSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

}
