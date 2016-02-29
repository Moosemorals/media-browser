package com.fluffypeople.pvrbrowser;

import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractListModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class DownloadListModel extends AbstractListModel implements StateChangeListener {

    private static final Logger log = LoggerFactory.getLogger(DownloadListModel.class);

    private final List<DownloadQueueItem> list = new ArrayList<>();

    @Override
    public int getSize() {
        return list.size();
    }

    @Override
    public Object getElementAt(int i) {
        return list.get(i);
    }

    public void add(DownloadQueueItem item) {
        list.add(item);
        item.addStateChangeListener(this);
        fireIntervalAdded(item, 0, list.size());
    }

    public void update(DownloadQueueItem item) {
        fireContentsChanged(item, 0, list.size());
    }

    public void remove(DownloadQueueItem item) {
        list.remove(item);
        fireIntervalRemoved(item, 0, list.size());
    }

    @Override
    public void stateChanged(DownloadQueueItem item) {
        log.debug("State changed");
        fireContentsChanged(item, 0, list.size());
    }

}
