package com.moosemorals.mediabrowser;

import com.moosemorals.mediabrowser.PVR.PVRFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric
 */
public class DownloadManager implements ListModel<PVRFile>, Runnable {

    private final Logger log = LoggerFactory.getLogger(DownloadManager.class);
    private final Preferences prefs;
    private final List<PVRFile> queue;
    private final AtomicBoolean running;
    private final Set<ListDataListener> listDataListeners;
    private Thread downloadThread;

    public DownloadManager(Preferences prefs) {
        this.prefs = prefs;
        this.queue = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.listDataListeners = new HashSet<>();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            downloadThread = new Thread(this, "Download");
            downloadThread.start();
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            downloadThread.interrupt();
            downloadThread = null;
        }
    }

    public boolean queueFile(PVRFile target) {
        if (target.getState() != PVRFile.State.Ready) {
            return false;
        }

        target.setDownloadPath(getDownloadPath().getPath());
        final File downloadTarget = new File(getDownloadPath(), target.getFilename());
        if (downloadTarget.exists()) {
            target.setDownloaded(downloadTarget.length());
            target.setState(PVRFile.State.Paused);
        } else {
            target.setDownloaded(0);
            target.setState(PVRFile.State.Queued);
        }

        synchronized (queue) {
            queue.add(target);
            log.debug("addTarget: Queue length: {}", queue.size());
            queue.notifyAll();
        }
        notifyListeners();
        return true;
    }

    @Override
    public void run() {
        while (running.get()) {
            PVRFile target;

            synchronized (queue) {
                while (!downloadsAvailible()) {
                    try {
                        queue.wait();
                    } catch (InterruptedException ex) {
                        log.info("Intrupted waiting for queue", ex);
                        running.set(false);
                        return;
                    }
                }
                log.debug("Run: Queue length: {}", queue.size());
                target = getNextDownload();
            }

            if (target == null) {
                throw new RuntimeException("Target is null, but it really shouldn't be");
            }

            log.debug("Downloading {} from {} ", target.getTitle(), target.getRemoteURL());
            target.setState(PVRFile.State.Downloading);
            notifyListeners();
            try {
                final File downloadTarget = new File(getDownloadPath(), target.getFilename());

                URL url = new URL(target.getRemoteURL());
                URLConnection connection = url.openConnection();

                boolean append = false;

                if (target.getState() == PVRFile.State.Queued) {
                    connection.setRequestProperty("Range", "bytes=" + target.getDownloaded() + "-");
                    append = true;
                }

                connection.connect();

                long lastCheck = System.currentTimeMillis();

                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(downloadTarget, append)) {
                    byte[] buffer = new byte[1024 * 4];
                    long count = target.getDownloaded();
                    int n = 0;
                    while (-1 != (n = in.read(buffer))) {
                        out.write(buffer, 0, n);
                        count += n;
                        target.setDownloaded(count);

                        if ((System.currentTimeMillis() - lastCheck) > 500) {
                            lastCheck = System.currentTimeMillis();
                            notifyListeners();
                        }

                        if (!running.get()) {
                            in.close();
                            target.setState(PVRFile.State.Paused);
                            notifyListeners();
                            return;
                        }
                    }
                }
                target.setState(PVRFile.State.Completed);
                notifyListeners();
            } catch (IOException ex) {
                log.error("IOExcption", ex);
            }
        }
    }

    private boolean downloadsAvailible() {
        for (PVRFile f : queue) {
            if (f.getState() == PVRFile.State.Queued || f.getState() == PVRFile.State.Paused) {
                return true;
            }
        }
        return false;
    }

    private PVRFile getNextDownload() {
        for (PVRFile f : queue) {
            if (f.getState() == PVRFile.State.Queued || f.getState() == PVRFile.State.Paused) {
                return f;
            }
        }
        return null;
    }

    public File getDownloadPath() {
        return new File(prefs.get(UI.KEY_DOWNLOAD_DIRECTORY, System.getProperty("user.home")));
    }

    public boolean isDownloadPathSet() {
        return prefs.get(UI.KEY_DOWNLOAD_DIRECTORY, null) != null;
    }

    public void setDownloadPath(File path) {
        prefs.put(UI.KEY_DOWNLOAD_DIRECTORY, path.getPath());
    }

    @Override
    public int getSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    @Override
    public PVRFile getElementAt(int i) {
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
