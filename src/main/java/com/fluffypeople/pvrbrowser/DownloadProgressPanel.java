/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 *
 * @author Osric
 */
public class DownloadProgressPanel extends JPanel {


    private final List<DownloadQueueItem> items = new ArrayList<>();
    private final List<DownloadProgressItem> pItems = new ArrayList<>();

    public DownloadProgressPanel() {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    }

    public void add(DownloadQueueItem item) {
        items.add(item);

        DownloadProgressItem p = new DownloadProgressItem(item);
        pItems.add(p);
        add(p);
        item.addStateChangeListener(p);
        item.addDownloadListener(p);
        getParent().revalidate();
    }

    @Override
    public Dimension getMinimumSize() {
        int width = 0;
        int height = 0;
        for (DownloadProgressItem p : pItems) {
            Dimension x = p.getPreferredSize();
            if (width < x.width) {
                width = x.width;
            }
            height += x.height;
        }
        if (width < 400) {
            width = 400;
        }
        if (height < 20) {
            height = 20;
        }
        return new Dimension(width, height);
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
