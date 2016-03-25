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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
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
public class DownloadManager implements ListModel<DownloadManager.QueueItem>, Runnable {

    private static final int BUFFER_SIZE = 1024 * 4;
    private final Logger log = LoggerFactory.getLogger(DownloadManager.class);
    private final Main main;
    private final Preferences prefs;
    private final List<QueueItem> queue;
    private final AtomicBoolean running;
    private boolean renaming = false;
    private final Set<ListDataListener> listDataListeners;
    private Thread downloadThread;
    private DownloadStatusListener status;
    private QueueItem current;

    DownloadManager(Main main) {
        this.main = main;
        this.prefs = main.getPreferences();
        this.queue = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.listDataListeners = new HashSet<>();
    }

    void start() {
        if (running.compareAndSet(false, true)) {
            if (areDownloadsAvailible()) {
                downloadThread = new Thread(this, "Download");
                downloadThread.start();
                status.downloadStatusChanged(true);
            }
        }
    }

    @Override
    public int getSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    @Override
    public QueueItem getElementAt(int i) {
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

    /**
     * Waits for the next download to get queued, and then downloads it.
     */
    @Override
    public void run() {
        try {
            while (running.get()) {
                QueueItem next;

                synchronized (queue) {
                    while (!areDownloadsAvailible()) {
                        queue.wait();
                    }
                    next = getNextItem();
                }

                if (next == null) {
                    throw new RuntimeException("Target is null, but it really shouldn't be");
                }

                current = next;
                try {
                    next.run();
                } catch (IOException ex) {
                    log.error("Unexpected issue with download: {}", ex.getMessage(), ex);
                    next.getTarget().setState(PVRFile.State.Error);
                }

                current = null;
                notifyListDataListeners();
                notifyStatusListeners();

                if (!prefs.getBoolean(Main.KEY_AUTO_DOWNLOAD, false) || !areDownloadsAvailible()) {
                    stop();
                    return;
                }

            }
        } catch (InterruptedException ex) {
            log.info("Interrupted waiting for next queue item. Assuming we're being told to stop");

        } finally {
            running.set(false);
            status.downloadStatusChanged(false);

            notifyListDataListeners();
            notifyStatusListeners();
        }
    }

    /**
     * Stops the download thread and any in-progress downloads. Waits for the
     * download thread to finish before returning.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (current != null) {
                current.stop();
            }

            downloadThread.interrupt();
            try {
                log.info("Waiting for download thread to finish");
                downloadThread.join();
            } catch (InterruptedException ex) {
                log.error("Interrupted while waiting for download thread to finish, ignoring");
            }
            downloadThread = null;

            notifyListDataListeners();
            notifyStatusListeners();
        }
    }

    /**
     * Add a file to the queue. Returns true if the file was added succesfully,
     * false otherwise.
     *
     * <p>
     * As part of the adding a file process, checks to see if the file is
     * partially downloaded, and updates the status (to
     * {@link PVR.PVRFile.State.Paused}), and the number of bytes already
     * downloaded, if needed.</p>
     *
     * @param target
     * @return
     */
    public boolean add(PVRFile target) {
        if (!setupForQueue(target)) {
            return false;
        }

        synchronized (queue) {
            if (queue.contains(target)) {
                // already queued
                return false;
            }

            queue.add(new DownloadQueueItem(this, target));
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
        return true;
    }

    /**
     * Get a copy of the current queue. Its a shallow copy, so try not to mess
     * with the PVRFiles too badly.
     *
     * @return
     */
    public List<QueueItem> getQueue() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
    }

    public void changeDownloadPath(List<QueueItem> files, File newPath) {
        synchronized (queue) {
            for (QueueItem item : files) {
                PVRFile file = item.getTarget();

                File oldTarget, newTarget, newComplete;

                newComplete = getCompletedTarget(file);

                if (newComplete.exists() && !main.askYesNoQuestion("Target file already exists, overwrite?")) {
                    continue;
                }

                switch (file.getState()) {
                    case Queued:
                        // Everything is fine, just update the path

                        oldTarget = file.getLocalPath();
                        file.setLocalPath(newPath);
                        newTarget = getDownloadTarget(file);

                        if (newTarget.exists()) {
                            log.debug("{} exists", newTarget);
                            file.setDownloaded(newTarget.length());
                            if (newTarget.length() != file.getSize()) {
                                file.setState(PVRFile.State.Paused);
                            } else {
                                file.setState(PVRFile.State.Completed);
                            }
                        } else {
                            log.debug("{} doesn't exist", newTarget);
                            file.setDownloaded(0);
                        }
                        log.debug("Changed download path from {} to {}", oldTarget, file.getLocalPath());
                        break;

                    case Paused:
                        // Not quite so easy. Need to move the partial file as well
                        oldTarget = getDownloadTarget(file);
                        file.setLocalPath(newPath);
                        newTarget = getDownloadTarget(file);

                        try {
                            Files.move(oldTarget.toPath(), newTarget.toPath());
                        } catch (IOException ex) {
                            log.error("Paused: Rename from [{}] to [{}] failed: {}", oldTarget, newTarget, ex, ex.getMessage());
                            file.setState(PVRFile.State.Error);
                        }

                        break;
                    case Downloading:
                        // Uh, a little more complicated. Pause the download,
                        // move the temp file, update the path and restart
                        // the download.
                        renaming = true;
                        stop();
                        oldTarget = getDownloadTarget(file);
                        file.setLocalPath(newPath);
                        newTarget = getDownloadTarget(file);

                        try {
                            Files.move(oldTarget.toPath(), newTarget.toPath());
                        } catch (IOException ex) {
                            log.error("Downloading: Rename from [{}] to [{}] failed: {}", oldTarget, newTarget, ex, ex.getMessage());
                            file.setState(PVRFile.State.Error);
                        }

                        renaming = false;
                        // Restart downloads.
                        start();

                        break;
                    case Completed:
                        // Easy again. Just move the file
                        oldTarget = getCompletedTarget(file);
                        file.setLocalPath(newPath);
                        newTarget = getCompletedTarget(file);
                        try {
                            Files.move(oldTarget.toPath(), newTarget.toPath());
                        } catch (IOException ex) {
                            log.error("Completed: Rename from [{}] to [{}] failed: {}", oldTarget, newTarget, ex, ex.getMessage());
                            file.setState(PVRFile.State.Error);
                        }
                }
            }
        }
        notifyListDataListeners();
    }

    /**
     * Move an (already queued) list of files to (before) the specified row.
     *
     * @param row int target row. Files will be inserted before this row.
     * @param files List&lt;PVRFile&gt; of files already in the list to move.
     */
    public void moveFiles(int row, List<QueueItem> files) {
        synchronized (queue) {
            for (QueueItem f : files) {
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
    public void insert(int row, List<PVRFile> files) {
        List<QueueItem> queueItems = new ArrayList<>();
        for (PVRFile f : files) {
            if (setupForQueue(f)) {
                queueItems.add(new DownloadQueueItem(this, f));
            }
        }

        if (queueItems.isEmpty()) {
            return;
        }

        synchronized (queue) {
            queue.addAll(row, queueItems);
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
    }

    /**
     * Remove a list of files from the queue. If any of the files are being
     * downloaded then that download will stop, but the partial file will be
     * left.
     *
     * @param items List of files to remove.
     */
    public void remove(List<QueueItem> items) {

        for (QueueItem i : items) {

            i.getTarget().setState(PVRFile.State.Ready);
        }

        synchronized (queue) {
            queue.removeAll(items);
            queue.notifyAll();
        }

        notifyListDataListeners();
        notifyStatusListeners();
    }

    /**
     * Checks if there is a download in progress.
     *
     * <p>
     * Strictly, checks if the download thread is running, but that should exit
     * once the queue is empty (or after the current download if
     * not-auto-download is set.
     *
     * @return boolean true if we're downloading, false otherwise.
     */
    public boolean isDownloading() {
        return running.get();
    }

    /**
     * Get the current download path if it has been set, or the users home
     * directory if not.
     *
     * @return File default download path.
     */
    public File getDownloadPath() {
        return new File(prefs.get(Main.KEY_DOWNLOAD_DIRECTORY, System.getProperty("user.home")));
    }

    /**
     * Check if the default download path has been set by the user.
     *
     * @return boolean true if the download path has been set.
     */
    public boolean isDownloadPathSet() {
        return prefs.get(Main.KEY_DOWNLOAD_DIRECTORY, null) != null;
    }

    /**
     * Set the default download path.
     *
     * @param path File path to download to.
     */
    public void setDownloadPath(File path) {
        if (path != null) {
            prefs.put(Main.KEY_DOWNLOAD_DIRECTORY, path.getPath());
        }
    }

    public void setDownloadStatusListener(DownloadStatusListener status) {
        this.status = status;
    }

    /**
     * Check if there are files queued and ready to download.
     *
     * @return boolean true if there are downloads available, false otherwise.
     */
    public boolean areDownloadsAvailible() {
        if (renaming) {
            return true;
        }
        synchronized (queue) {
            for (QueueItem i : queue) {
                PVRFile f = i.getTarget();
                if (f.getState() == PVRFile.State.Queued || f.getState() == PVRFile.State.Paused) {
                    return true;
                }
            }
        }
        return false;
    }

    private void notifyListDataListeners() {
        final ListDataEvent lde = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, 0, queue.size());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (listDataListeners) {
                    for (ListDataListener ll : listDataListeners) {
                        ll.contentsChanged(lde);
                    }
                }
            }
        });

    }

    private void notifyStatusListeners() {
        notifyStatusListeners(-1.0);
    }

    private void notifyStatusListeners(double rate) {
        if (renaming) {
            return;
        }

        if (status == null) {
            return;
        }
        long totalQueued = 0;
        long totalDownloaded = 0;
        long currentFile = 0;
        long currentDownload = 0;
        synchronized (queue) {
            for (QueueItem i : queue) {
                PVRFile f = i.getTarget();
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

    private boolean setupForQueue(PVRFile target) {

        if (target.getState() != PVRFile.State.Ready) {
            log.info("File {} isn't ready, in state {}", target.getTitle(), target.getState());
            return false;
        }

        if (target.getRemoteURL() == null) {
            log.info("File {} isn't ready, no remote URL", target.getTitle());
            return false;
        }

        if (target.getLocalPath() == null) {
            target.setLocalPath(new File(getDownloadPath().getPath()));
        }
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

    private QueueItem getNextItem() {
        for (QueueItem i : queue) {
            PVRFile f = i.getTarget();
            if (f.getState() == PVRFile.State.Queued || f.getState() == PVRFile.State.Paused) {
                return i;
            }
        }
        return null;
    }

    private static File getDownloadTarget(PVRFile target) {
        return new File(target.getLocalPath(), target.getLocalFilename() + ".partial");
    }

    private static File getCompletedTarget(PVRFile target) {
        return new File(target.getLocalPath(), target.getLocalFilename() + ".ts");
    }

    public interface DownloadStatusListener {

        public void downloadStatusChanged(boolean running);

        public void downloadProgress(long totalQueued, long totalDownloaded, long currentFile, long currentDownloaded, double rate);

        public void downloadCompleted(PVRFile target);
    }

    public static abstract class QueueItem {

        protected final AtomicBoolean running;
        protected final DownloadManager parent;
        protected final PVRFile target;

        public QueueItem(DownloadManager parent, PVRFile target) {
            this.parent = parent;
            this.target = target;
            this.running = new AtomicBoolean(false);
        }

        abstract public void run() throws IOException;

        public void stop() {
            if (running.compareAndSet(true, false)) {
                running.set(false);
            }
        }

        public PVRFile getTarget() {
            return target;
        }
    }

    private static class DownloadQueueItem extends QueueItem {

        private final Logger log = LoggerFactory.getLogger(DownloadQueueItem.class);

        public DownloadQueueItem(DownloadManager parent, PVRFile target) {
            super(parent, target);
        }

        @Override
        public void run() throws IOException {
            running.set(true);

            File downloadTarget = getDownloadTarget(target);
            URL url = new URL(target.getRemoteURL());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            boolean append = false;

            if (target.getState() == PVRFile.State.Paused) {
                connection.setRequestProperty("Range", "bytes=" + target.getDownloaded() + "-");
            }

            log.info("Downloading {} from {} ", target.getTitle(), target.getRemoteURL());
            connection.connect();

            if (connection.getResponseCode() == 206) {
                // Partial content
                log.debug("Connection says sure, partial is fine: {}", connection.getHeaderField("Content-Range"));
                append = true;
            }

            target.setState(PVRFile.State.Downloading);

            long lastCheck = System.currentTimeMillis();
            long lastDownloaded = target.getDownloaded();

            try (InputStream in = new BufferedInputStream(connection.getInputStream()); OutputStream out = new BufferedOutputStream(new FileOutputStream(downloadTarget, append))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long count = target.getDownloaded();
                int bytesRead;

                // Main download loop. Isn't it well hidden?
                while ((bytesRead = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    count += bytesRead;
                    target.setDownloaded(count);

                    long timeNow = System.currentTimeMillis();
                    if ((timeNow - lastCheck) > 750) {
                        // calculate rate, in bytes/ms
                        double rate = (target.getDownloaded() - lastDownloaded) / (double) (timeNow - lastCheck);
                        // Make it bytes/second
                        rate *= 1000;

                        parent.notifyListDataListeners();
                        parent.notifyStatusListeners(rate);

                        lastCheck = timeNow;
                        lastDownloaded = target.getDownloaded();
                    }

                    // We can be asked to stop downloading in two ways. Either running is
                    // set false, or the user sets our state to paused. Either way, we're
                    // done here.
                    if (!running.get() || target.getState() != PVRFile.State.Downloading) {
                        in.close();
                        break;
                    }
                }

                // Paranoia.
                out.flush();

            } catch (IOException ex) {
                if (running.get()) {
                    // Error while we're running, so probably actualy an error
                    target.setState(PVRFile.State.Error);
                    log.error("IOException while downloading: {}", ex.getMessage(), ex);
                } else {
                    // Error while we're not running, probably a disconnect
                    target.setState(PVRFile.State.Paused);
                    log.info("IOException disconnecting: {}", ex.getMessage(), ex);
                }
                // just in case
                stop();
                return;
            }

            // Assume that if the file on disk is the same size as the
            // file we were told about then then file has downloaded
            // ok. Oh, for some kind of hash from the remote end.
            if (target.getSize() == downloadTarget.length()) {
                File completed = getCompletedTarget(target);
                if (downloadTarget.renameTo(completed)) {
                    completed.setLastModified(target.getStartTime().getMillis());
                    target.setState(PVRFile.State.Completed);
                } else {
                    log.error("Can't rename {} to {}", target, completed);
                    target.setState(PVRFile.State.Error);
                }
                parent.status.downloadCompleted(target);
            } else {
                // Assume that we got interrupted for a good and proper reason.
                // Error status is set above.
                target.setState(PVRFile.State.Paused);
            }
        }
    }
}
