/*
 * The MIT License
 *
 * Copyright 2016 Osric Wilkinson <osric@fluffypeople.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.moosemorals.mediabrowser;

import com.moosemorals.mediabrowser.PVR.PVRFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
 * Keep track of files that need downloading and their status, and then download
 * them.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
class DownloadManager implements ListModel<PVRFile>, Runnable {

    private final Logger log = LoggerFactory.getLogger(DownloadManager.class);
    private final Preferences prefs;
    private final List<PVRFile> queue;
    private final AtomicBoolean running;
    private final Set<ListDataListener> listDataListeners;
    private Thread downloadThread;
    private DownloadStatusListener status;

    DownloadManager(Preferences prefs) {
        this.prefs = prefs;
        this.queue = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.listDataListeners = new HashSet<>();
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            if (downloadsAvailible()) {
                downloadThread = new Thread(this, "Download");
                downloadThread.start();
                status.downloadStatusChanged(true);
            }
        }
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
                        status.downloadStatusChanged(false);
                        notifyListDataListeners();
                        notifyStatusListeners();
                        return;
                    }
                }
                log.debug("Run: Queue length: {}", queue.size());
                target = getNextDownload();
            }

            if (target == null) {
                throw new RuntimeException("Target is null, but it really shouldn't be");
            }

            notifyListDataListeners();
            try {

                File downloadTarget = getDownloadTarget(target);
                URL url = new URL(target.getRemoteURL());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                boolean append = false;

                if (target.getState() == PVRFile.State.Paused) {
                    connection.setRequestProperty("Range", "bytes=" + target.getDownloaded() + "-");
                }

                log.debug("Downloading {} from {} ", target.getTitle(), target.getRemoteURL());
                connection.connect();

                if (connection.getResponseCode() == 206) {
                    // Partial content
                    log.debug("Connection says sure, partial is fine: {}", connection.getHeaderField("Content-Range"));
                    append = true;
                }

                target.setState(PVRFile.State.Downloading);

                long lastCheck = System.currentTimeMillis();
                long lastDownloaded = target.getDownloaded();

                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(downloadTarget, append)) {
                    byte[] buffer = new byte[1024 * 4];
                    long count = target.getDownloaded();
                    int n = 0;
                    while (-1 != (n = in.read(buffer))) {
                        out.write(buffer, 0, n);
                        count += n;
                        target.setDownloaded(count);

                        long timeNow = System.currentTimeMillis();
                        if ((timeNow - lastCheck) > 500) {
                            // calculate rate, in bytes/ms
                            double rate = (target.getDownloaded() - lastDownloaded) / (double) (timeNow - lastCheck);
                            // Make it bytes/second
                            rate *= 1000;

                            notifyListDataListeners();
                            notifyStatusListeners(rate);

                            lastCheck = timeNow;
                            lastDownloaded = target.getDownloaded();
                        }

                        if (!running.get() || target.getState() != PVRFile.State.Downloading) {
                            in.close();
                            target.setState(PVRFile.State.Paused);
                            notifyListDataListeners();
                            return;
                        }
                    }
                }

                if (target.getSize() == downloadTarget.length()) {
                    File completed = getCompletedTarget(target);
                    if (!downloadTarget.renameTo(completed)) {
                        throw new IOException("Can't rename from " + downloadTarget + " to " + completed + ": Unknown");
                    }
                    target.setState(PVRFile.State.Completed);
                } else {
                    target.setState(PVRFile.State.Error);
                }

                notifyListDataListeners();

                status.downloadCompleted(target);

                if (!prefs.getBoolean(UI.KEY_AUTO_DOWNLOAD, false)) {
                    stop();
                    status.downloadStatusChanged(false);
                    return;
                }
            } catch (IOException ex) {
                log.error("IOExcption", ex);
            }
        }
    }

    void stop() {
        if (running.compareAndSet(true, false)) {
            downloadThread.interrupt();
            downloadThread = null;
            status.downloadStatusChanged(false);
            notifyListDataListeners();
            notifyStatusListeners();
        }
    }

    boolean add(PVRFile target) {
        if (!setupForQueue(target)) {
            return false;
        }

        synchronized (queue) {
            queue.add(target);
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
        return true;
    }

    void changeDownloadPath(List<PVRFile> files, File newPath) {

    }

    /**
     * Move an (already queued) list of files to (before) the specified row.
     *
     * @param row int target row. Files will be inserted before this row.
     * @param files List&lt;PVRFile&gt; of files already in the list to move.
     */
    void moveFiles(int row, List<PVRFile> files) {
        synchronized (queue) {
            for (PVRFile f : files) {
                queue.remove(f);
                queue.add(row, f);
            }
            queue.notifyAll();
        }
        notifyListDataListeners();
        notifyStatusListeners();
    }

    /**
     * Queue a list of files not already queued.
     *
     * @param row int target row. Files will be inserted before this row.
     * @param files List&ltPVRFile&gt; of files to be inserted
     */
    void insert(int row, List<PVRFile> files) {
        log.debug("Inserting {} files at row {}", files.size(), row);

        for (Iterator<PVRFile> it = files.iterator(); it.hasNext();) {
            PVRFile f = it.next();
            if (!setupForQueue(f)) {
                it.remove();
            }
        }

        if (files.isEmpty()) {
            log.debug("Nothing left to insert");
            return;
        }

        synchronized (queue) {
            queue.addAll(row, files);
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
    }

    void remove(List<PVRFile> files) {

        for (PVRFile f : files) {
            f.setState(PVRFile.State.Ready);
        }

        synchronized (queue) {
            queue.removeAll(files);
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
    }

    boolean isDownloading() {
        return running.get();
    }

    File getDownloadPath() {
        return new File(prefs.get(UI.KEY_DOWNLOAD_DIRECTORY, System.getProperty("user.home")));
    }

    boolean isDownloadPathSet() {
        return prefs.get(UI.KEY_DOWNLOAD_DIRECTORY, null) != null;
    }

    void setDownloadPath(File path) {
        if (path != null) {
            prefs.put(UI.KEY_DOWNLOAD_DIRECTORY, path.getPath());
        }
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

    private void notifyListDataListeners() {
        final ListDataEvent lde = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, queue.size());
        synchronized (listDataListeners) {
            for (ListDataListener ll : listDataListeners) {
                ll.contentsChanged(lde);
            }
        }
    }

    private void notifyStatusListeners() {
        notifyStatusListeners(-1.0);
    }

    private void notifyStatusListeners(double rate) {
        if (status == null) {
            return;
        }
        long totalQueued = 0;
        long totalDownloaded = 0;
        long currentFile = 0;
        long currentDownload = 0;
        synchronized (queue) {
            for (PVRFile f : queue) {
                totalQueued += f.getSize();
                totalDownloaded += f.getDownloaded();
                if (f.getState() == PVRFile.State.Downloading) {
                    currentFile = f.getSize();
                    currentDownload = f.getDownloaded();
                }
            }
        }

        status.downloadProgress(totalQueued, totalDownloaded, currentFile, currentDownload, rate);
    }

    void setDownloadStatusListener(DownloadStatusListener status) {
        this.status = status;
    }

    private boolean setupForQueue(PVRFile target) {
        if (target.getState() != PVRFile.State.Ready) {
            log.debug("File {} isn't ready, in state {}", target.getTitle(), target.getState());
            return false;
        }

        target.setDownloadPath(new File(getDownloadPath().getPath()));
        final File downloadTarget = getDownloadTarget(target);
        if (downloadTarget.exists()) {
            target.setDownloaded(downloadTarget.length());
            if (downloadTarget.length() == target.getSize()) {
                target.setState(PVRFile.State.Completed);
                return false;
            } else {
                target.setState(PVRFile.State.Paused);
            }
        } else {
            target.setDownloaded(0);
            target.setState(PVRFile.State.Queued);
        }
        return true;
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

    private File getDownloadTarget(PVRFile target) {
        return new File(target.getDownloadPath(), target.getDownloadFilename() + ".partial");
    }

    private File getCompletedTarget(PVRFile target) {
        return new File(target.getDownloadPath(), target.getDownloadFilename() + ".ts");
    }

    interface DownloadStatusListener {

        void downloadStatusChanged(boolean running);

        void downloadProgress(long totalQueued, long totalDownloaded, long currentFile, long currentDownloaded, double rate);

        void downloadCompleted(PVRFile target);
    }
}
