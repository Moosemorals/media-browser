package com.fluffypeople.pvrbrowser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.fourthline.cling.support.model.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class DownloadManager implements ListModel<RemoteItem>, Runnable {

    private final Logger log = LoggerFactory.getLogger(DownloadManager.class);
    private final Preferences prefs;
    private final List<RemoteItem> queue;
    private final AtomicBoolean running;
    private final HttpClient client;
    private final Set<ListDataListener> listDataListeners;
    private int pointer = 0;

    public DownloadManager(Preferences prefs) {
        this.prefs = prefs;
        this.queue = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.client = new DefaultHttpClient();
        this.listDataListeners = new HashSet<>();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread t = new Thread(this, "Download");
            t.start();
        }
    }

    public void addTarget(Item target) {
        RemoteItem item = new RemoteItem(target);
        item.setDownloadPath(getDownloadPath().getPath());
        synchronized (queue) {
            queue.add(item);
            log.debug("addTarget: Queue length: {}", queue.size());
            queue.notifyAll();
        }
        notifyListeners();
    }

    @Override
    public void run() {
        while (running.get()) {
            RemoteItem target;

            synchronized (queue) {
                while (pointer == queue.size()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException ex) {
                        log.error("Unexpected Iterruption", ex);
                        running.set(false);
                        return;
                    }
                }
                log.debug("Run: Queue length: {}", queue.size());
                target = queue.get(pointer++);
            }

            Item item = target.getTarget();
            log.debug("Downloading " + item.getTitle());
            target.setState(RemoteItem.State.DOWNLOADING);
            notifyListeners();
            try {

                final HttpGet request = new HttpGet(item.getFirstResource().getValue());
                final HttpResponse response = client.execute(request);

                final StatusLine result = response.getStatusLine();
                if (result.getStatusCode() != 200) {
                    log.error("Can't download item");
                    continue;
                }

                long len = Long.parseLong(response.getFirstHeader("Content-Length").getValue(), 10);
                target.setSize(len);
                final HttpEntity body = response.getEntity();

                log.debug("Downloading " + len + " bytes");

                final File downloadTarget = new File(getDownloadPath(), item.getTitle());
                log.debug("Filename " + downloadTarget.getAbsolutePath());

                try (InputStream in = body.getContent(); OutputStream out = new FileOutputStream(downloadTarget)) {
                    byte[] buffer = new byte[1024 * 4];
                    long count = 0;
                    int n = 0;
                    while (-1 != (n = in.read(buffer))) {
                        out.write(buffer, 0, n);
                        count += n;
                        target.setDownloaded(count);
                        notifyListeners();
                    }
                }

                target.setState(RemoteItem.State.COMPLETED);
                notifyListeners();
            } catch (IOException ex) {
                log.error("IOExcption", ex);
            }
        }
    }

    public File getDownloadPath() {
        return new File(prefs.get(UI.DOWNLOAD_DIRECTORY_KEY, System.getProperty("user.home")));
    }

    public boolean isDownloadPathSet() {
        return prefs.get(UI.DOWNLOAD_DIRECTORY_KEY, null) != null;
    }

    public void setDownloadPath(File path) {
        prefs.put(UI.DOWNLOAD_DIRECTORY_KEY, path.getPath());
    }

    @Override
    public int getSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    @Override
    public RemoteItem getElementAt(int i) {
        synchronized (queue) {
            return queue.get(i);
        }
    }

    @Override
    public void addListDataListener(ListDataListener ll) {
        synchronized (listDataListeners) {
            listDataListeners.add(ll);
        }
    }

    @Override
    public void removeListDataListener(ListDataListener ll) {
        synchronized (listDataListeners) {
            listDataListeners.remove(ll);
        }
    }

    private void notifyListeners() {
        final ListDataEvent lde = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, queue.size());
        synchronized (listDataListeners) {
            for (ListDataListener ll : listDataListeners) {
                ll.contentsChanged(lde);
            }
        }
    }
}
