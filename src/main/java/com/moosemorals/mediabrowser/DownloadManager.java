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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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
public final class DownloadManager implements ListModel<DownloadManager.QueueItem>, Runnable {

    private static final int BUFFER_SIZE = 1024 * 4; // bytes
    private static final int UPDATE_INTERVAL = 500; // miliseconds
    private static DownloadManager instance;

    private final Logger log = LoggerFactory.getLogger(DownloadManager.class);
    private final Main main;
    private final Preferences prefs;
    private final List<QueueItem> queue;
    private final AtomicBoolean running;
    private final Set<ListDataListener> listDataListeners;
    private final Set<DownloadStatusListener> statusListeners;
    private final MoveManager moveManager;

    private Thread downloadThread;

    private QueueItem current;

    private DownloadManager(Main main) {
        this.main = main;
        this.prefs = main.getPreferences();
        this.queue = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        this.listDataListeners = new HashSet<>();
        this.statusListeners = new HashSet<>();
        this.moveManager = new MoveManager(this);
        moveManager.start();
    }

    public static DownloadManager getInstance() {
        return instance;
    }

    static DownloadManager createInstance(Main main) {
        instance = new DownloadManager(main);
        return instance;
    }

    void start() {
        if (running.compareAndSet(false, true)) {

            if (areDownloadsAvailible()) {
                downloadThread = new Thread(this, "Download");
                downloadThread.start();
                notifyDownloadStatusChanged(true);
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

    public void addDownloadStatusListener(DownloadStatusListener dsl) {
        synchronized (statusListeners) {
            statusListeners.add(dsl);
        }
    }

    public void removeDownloadStatusListener(DownloadStatusListener dsl) {
        synchronized (statusListeners) {
            statusListeners.remove(dsl);
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
                    next.download();
                } catch (IOException ex) {
                    log.error("Unexpected issue with download: {}", ex.getMessage(), ex);
                    next.setState(QueueItem.State.Error);
                }

                current = null;
                notifyListDataListeners();
                notifyStatusListeners();

                if (!prefs.getBoolean(Main.KEY_AUTO_DOWNLOAD, false) || !areDownloadsAvailible()) {
                    log.debug("Stopping downloads : Auto download {} areAvailible {}", prefs.getBoolean(Main.KEY_AUTO_DOWNLOAD, false), areDownloadsAvailible());
                    stop();
                    return;
                }

            }
        } catch (InterruptedException ex) {
            log.info("Interrupted waiting for next queue item. Assuming we're being told to stop");

        } finally {
            running.set(false);
            notifyDownloadStatusChanged(false);

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
     * {@link PVR.State.Paused}), and the number of bytes already downloaded, if
     * needed.</p>
     *
     * @param target
     * @return
     */
    public boolean add(PVRFile target) {
        return add(target, null);
    }

    public boolean add(PVRFile target, String localPath) {
        if (!target.isQueueable()) {
            return false;
        }

        synchronized (queue) {
            QueueItem item = null;
            for (QueueItem i : queue) {
                if (i.getTarget().equals(target)) {
                    item = i;
                    break;
                }
            }

            if (item == null) {
                item = createQueueItem(target, localPath);
                item.setState(QueueItem.State.Queued);
                queue.add(item);
            }

            item.checkTarget();

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
                item.rename(newPath);
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
     * @param items List&ltPVRFile&gt; of files to be inserted
     */
    public void insert(int row, List<QueueItem> items) {

        if (items.isEmpty()) {
            return;
        }

        synchronized (queue) {

            for (Iterator<QueueItem> it = items.iterator(); it.hasNext();) {
                QueueItem item = it.next();

                if (queue.contains(item)) {
                    it.remove();
                }

                item.setState(QueueItem.State.Queued);
            }

            queue.addAll(row, items);
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

        synchronized (queue) {
            for (Iterator<QueueItem> it = queue.iterator(); it.hasNext();) {
                QueueItem i = it.next();
                if (items.contains(i)) {
                    it.remove();
                }
            }
            queue.notifyAll();
        }

        for (QueueItem i : items) {
            i.setState(QueueItem.State.Ready);
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

    /**
     * Check if there are files queued and ready to download.
     *
     * @return boolean true if there are downloads available, false otherwise.
     */
    private boolean areDownloadsAvailible() {
        synchronized (queue) {
            for (QueueItem i : queue) {
                if (i.getState() == QueueItem.State.Queued || i.getState() == QueueItem.State.Paused || i.getState() == QueueItem.State.Moving) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean shouldEnableDownloadButton() {
        synchronized (queue) {
            for (QueueItem i : queue) {
                if (i.getState() == QueueItem.State.Queued
                        || i.getState() == QueueItem.State.Paused
                        || i.getState() == QueueItem.State.Moving
                        || i.getState() == QueueItem.State.Downloading) {
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

    private void notifyListDataListeners(QueueItem item) {
        int index;
        synchronized (queue) {
            index = queue.indexOf(item);
        }
        if (index == -1) {
            // Happens a lot more than I was expecting.
            return;
        }

        final ListDataEvent lde = new ListDataEvent(this, ListDataEvent.CONTENTS_CHANGED, index, index + 1);
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

    private void notifyDownloadStatusChanged(boolean status) {
        synchronized (statusListeners) {
            for (DownloadStatusListener dsl : statusListeners) {
                dsl.onDownloadStatusChanged(status);
            }
        }
    }

    private void notifyDownloadCompleted(QueueItem target) {
        synchronized (statusListeners) {
            for (DownloadStatusListener dsl : statusListeners) {
                dsl.onDownloadCompleted(target);
            }
        }
    }

    private void notifyStatusListeners() {
        notifyStatusListeners(-1.0);
    }

    private void notifyStatusListeners(double rate) {

        long totalQueued = 0;
        long totalDownloaded = 0;

        synchronized (queue) {
            for (QueueItem i : queue) {
                totalQueued += i.getTarget().getSize();
                totalDownloaded += i.getDownloaded();
            }
        }

        synchronized (statusListeners) {
            for (DownloadStatusListener dsl : statusListeners) {
                dsl.onDownloadProgress(totalQueued, totalDownloaded, rate);
            }
        }
    }

    public QueueItem createQueueItem(PVRFile target, String path) {
        QueueItem result = new QueueItem(target);
        if (path != null) {
            result.setLocalPath(new File(path));
        } else {
            result.setLocalPath(getDownloadPath());
        }

        return result;
    }

    private QueueItem getNextItem() {
        for (QueueItem i : queue) {
            if (i.getState() == QueueItem.State.Queued || i.getState() == QueueItem.State.Paused) {
                return i;
            }
        }
        return null;
    }

    public interface DownloadStatusListener {

        /**
         * Called when downloads start (true) or stop (false).
         *
         * @param running boolean true if downloading, false otherwise.
         */
        public void onDownloadStatusChanged(boolean running);

        /**
         * Update listeners with the progress of ongoing downloads.
         *
         * @param totalQueued long How many bytes are queued.
         * @param totalDownloaded long How many bytes have been downloaded.
         * @param rate double current download speed, in bytes/second
         */
        public void onDownloadProgress(long totalQueued, long totalDownloaded, double rate);

        /**
         * Called when a QueueItem has finished downloading.
         *
         * @param target QueueItem that has finished downloading.
         */
        public void onDownloadCompleted(QueueItem target);
    }

    public static class QueueItem {

        private final Logger log = LoggerFactory.getLogger(QueueItem.class);
        private final AtomicBoolean running;
        private final DownloadManager parent;
        private final PVRFile target;

        private float moveProgress = 0;
        private long downloaded = -1;
        private File localPath = null;
        private String localFilename = null;
        private State state;
        private State oldState;

        public QueueItem(PVRFile target) {
            this.running = new AtomicBoolean(false);
            this.parent = DownloadManager.getInstance();
            this.target = target;
            this.state = this.oldState = State.Ready;

            localFilename = target.getRemoteFilename();
        }

        public void stop() {
            if (running.compareAndSet(true, false)) {
                running.set(false);
            }
        }

        public PVRFile getTarget() {
            return target;
        }

        /**
         * Set the local folder where this file should be saved.
         *
         * @param localPath File local folder
         * @throws IllegalArgumentException if localPath is not a folder.
         */
        void setLocalPath(File localPath) {
            if (!localPath.isDirectory()) {
                throw new IllegalArgumentException(localPath + " is not a folder");
            }
            this.localPath = localPath;
        }

        /**
         * Get the local folder where this file should be saved.
         *
         * @return File local folder
         */
        public File getLocalPath() {
            return localPath;
        }

        /**
         * Get the filename that will be used when this file is saved locally.
         *
         * @return String local filename
         */
        public String getLocalFilename() {
            return localFilename;
        }

        /**
         * Set the local filename to use when saving this file locally.
         *
         * @param localFilename local filename
         */
        void setLocalFilename(String localFilename) {
            this.localFilename = localFilename;
            parent.notifyListDataListeners(this);
        }

        /**
         * Get how many bytes of this file have been downloaded.
         *
         * @return long downloaded bytes.
         */
        public long getDownloaded() {
            return downloaded;
        }

        /**
         * Set how many bytes of this file have been downloaded.
         *
         * @param downloaded
         */
        void setDownloaded(long downloaded) {
            this.downloaded = downloaded;
            parent.notifyListDataListeners(this);
        }

        /**
         * Sets the queue state of this file.
         *
         * @param newState
         */
        void setState(State newState) {
            oldState = this.state;
            this.state = newState;
            parent.notifyListDataListeners(this);
        }

        /**
         * Get the current queue/download state.
         *
         * @return
         */
        public State getState() {
            return state;
        }

        State getOldState() {
            return oldState;
        }

        public long getSize() {
            return target.getSize();
        }

        public void checkTarget() {
            localFilename = String.format("%s - %s - [%s - Freeview - %s] UNEDITED",
                    target.getTitle().replaceAll("[/?<>\\:*|\"^]", "_"),
                    PVR.FILE_DATE_FORMAT.print(target.getStartTime()),
                    target.isHighDef() ? "1920x1080" : "SD",
                    target.getChannelName()
            );

            final File downloadTarget = getDownloadTarget();
            if (downloadTarget.exists()) {
                setDownloaded(downloadTarget.length());
                if (downloadTarget.length() == target.getSize()) {
                    setState(QueueItem.State.Completed);
                } else {
                    setState(QueueItem.State.Paused);
                }
            } else {
                setDownloaded(0);
            }
        }

        File getDownloadTarget() {
            return new File(getLocalPath(), getLocalFilename() + ".partial");
        }

        File getCompletedTarget() {
            return new File(getLocalPath(), getLocalFilename() + ".ts");
        }

        public void download() throws IOException {
            running.set(true);

            File downloadTarget = getDownloadTarget();
            URL url = new URL(target.getRemoteURL());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            boolean append = false;

            if (getState() == State.Paused) {
                connection.setRequestProperty("Range", "bytes=" + getDownloaded() + "-");
            }

            log.info("Downloading {} from {} ", target.getTitle(), target.getRemoteURL());
            connection.connect();

            if (connection.getResponseCode() == 206) {
                // Partial content
                log.debug("Connection says sure, partial is fine: {}", connection.getHeaderField("Content-Range"));
                append = true;
            }

            setState(State.Downloading);

            long lastDisplay = System.currentTimeMillis();
            long lastDownloaded = getDownloaded();

            try (InputStream in = new BufferedInputStream(connection.getInputStream()); OutputStream out = new BufferedOutputStream(new FileOutputStream(downloadTarget, append))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long count = getDownloaded();
                int bytesRead;

                // Main download loop. Isn't it well hidden?
                while ((bytesRead = in.read(buffer, 0, BUFFER_SIZE)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    count += bytesRead;
                    setDownloaded(count);

                    long timeNow = System.currentTimeMillis();
                    if ((timeNow - lastDisplay) > UPDATE_INTERVAL) {
                        // calculate rate, in bytes/ms
                        double rate = (getDownloaded() - lastDownloaded) / (double) (timeNow - lastDisplay);
                        // Make it bytes/second
                        rate *= 1000;

                        parent.notifyListDataListeners();
                        parent.notifyStatusListeners(rate);

                        lastDisplay = timeNow;
                        lastDownloaded = getDownloaded();
                    }

                    // We can be asked to stop downloading in two ways. Either running is
                    // set false, or the user sets our state to paused. Either way, we're
                    // done here.
                    if (!running.get() || getState() != State.Downloading) {
                        in.close();
                        break;
                    }
                }

                // Paranoia.
                out.flush();

            } catch (IOException ex) {
                if (running.get()) {
                    // Error while we're running, so probably actualy an error
                    setState(State.Error);
                    log.error("IOException while downloading: {}", ex.getMessage(), ex);
                } else {
                    // Error while we're not running, probably a disconnect
                    setState(State.Paused);
                    log.info("IOException disconnecting: {}", ex.getMessage(), ex);
                }
                // just in case
                stop();
                return;
            }

            // Check for move
            if (getState() == State.Moving) {
                try {
                    Files.move(downloadTarget.toPath(), getDownloadTarget().toPath());
                    setState(State.Paused);
                } catch (IOException ex) {
                    log.error("Downloading: Rename from [{}] to [{}] failed: {}", downloadTarget, getDownloadTarget(), ex, ex.getMessage());
                    setState(State.Error);
                }
            }

            // Assume that if the file on disk is the same size as the
            // file we were told about then then file has downloaded
            // ok. Oh, for some kind of hash from the remote end.
            if (target.getSize() == downloadTarget.length()) {
                File completed = getCompletedTarget();
                if (downloadTarget.renameTo(completed)) {
                    completed.setLastModified(target.getStartTime().getMillis());
                    setState(State.Completed);
                } else {
                    log.error("Can't rename {} to {}", target, completed);
                    setState(State.Error);
                }
                parent.notifyDownloadCompleted(this);
            } else {
                // Assume that we got interrupted for a good and proper reason.
                // Error status is set above.
                setState(State.Paused);
            }
        }

        public void rename(File newPath) {
            File oldTarget, newTarget, newComplete;

            newComplete = getCompletedTarget();

            if (newComplete.exists() && !parent.main.askYesNoQuestion("Target file already exists, overwrite?")) {
                return;
            }

            switch (getState()) {
                case Queued:
                    // Everything is fine, just update the path

                    oldTarget = getLocalPath();
                    setLocalPath(newPath);
                    newTarget = getDownloadTarget();

                    if (newTarget.exists()) {
                        log.debug("{} exists", newTarget);
                        setDownloaded(newTarget.length());
                        if (newTarget.length() != target.getSize()) {
                            setState(State.Paused);
                        } else {
                            setState(State.Completed);
                        }
                    } else {
                        log.debug("{} doesn't exist", newTarget);
                        setDownloaded(0);
                    }
                    log.debug("Changed download path from {} to {}", oldTarget, getLocalPath());
                    break;
                case Downloading:

                    setLocalPath(newPath);
                    setState(State.Moving);

                    break;
                case Completed:
                case Paused:

                    setState(State.Moving);
                    parent.moveManager.add(this, newPath);

                    break;

            }
        }

        public float getMoveProgress() {
            return moveProgress;
        }

        void setMoveProgress(float p) {
            moveProgress = p;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + target.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final QueueItem other = (QueueItem) obj;
            return target.equals(other.target);
        }

        /**
         * Indicates the queued/downloading state of a file.
         */
        public static enum State {
            /**
             * Found, in the tree, but not queued.
             */
            Ready, /**
             * In the download queue.
             */
            Queued, /**
             * Currently downloading. Should only be one PVRFile in this state.
             *
             */
            Downloading, /**
             * Download started, but not finished. Proabably means theres a
             * '.partial' file in the localPath folder.
             */
            Paused,
            /**
             * File is being moved.
             *
             */
            Moving,
            /**
             * File has downloaded succesfully. At least, as far as we can
             * tell....
             */
            Completed, /**
             * The file got broken somehow.
             */
            Error

            /**
             * Queue and handle move operation for items that aren't being
             * downloaded.
             *
             * @author Osric Wilkinson <osric@fluffypeople.com>
             */
        }

    }

    private static class MoveManager implements Runnable {

        private final Logger log = LoggerFactory.getLogger(MoveManager.class);
        private final List<Pair> queue;
        private final AtomicBoolean running;
        private final DownloadManager parent;
        private Thread moveThread;

        MoveManager(DownloadManager parent) {
            this.parent = parent;
            queue = new LinkedList<>();
            running = new AtomicBoolean(false);
        }

        void start() {
            if (running.compareAndSet(false, true)) {
                moveThread = new Thread(this, "Move");
                moveThread.start();
            }
        }

        void stop() {
            if (running.compareAndSet(true, false)) {
                if (moveThread != null) {
                    moveThread.interrupt();
                }
            }
        }

        void add(QueueItem item, File newPath) {
            synchronized (queue) {
                Pair p = new Pair(item, newPath);
                for (Pair q : queue) {
                    if (q.queueItem.equals(item)) {
                        log.debug("already queued, updating destination path", item, newPath);
                        q.newPath = newPath;
                        break;
                    }
                }
                if (!queue.contains(p)) {
                    log.debug("Not already queued, adding");
                    queue.add(p);
                }
                queue.notifyAll();
            }
        }

        @Override
        public void run() {
            while (running.get()) {
                Pair next;
                try {
                    synchronized (queue) {
                        while (queue.isEmpty()) {
                            log.debug("Waiting for next move");
                            queue.wait();
                        }
                        next = queue.remove(0);
                    }
                } catch (InterruptedException ex) {
                    // Interrupted while waiting
                    return;
                }
                File oldTarget;
                File newTarget;
                switch (next.queueItem.getOldState()) {
                    case Paused:
                        oldTarget = next.queueItem.getDownloadTarget();
                        next.queueItem.setLocalPath(next.newPath);
                        newTarget = next.queueItem.getDownloadTarget();
                        break;
                    case Completed:
                        oldTarget = next.queueItem.getDownloadTarget();
                        next.queueItem.setLocalPath(next.newPath);
                        newTarget = next.queueItem.getDownloadTarget();
                        break;
                    default:
                        log.error("Queue item in unexpected state {}, not moving", next.queueItem.getOldState());
                        next.queueItem.setState(QueueItem.State.Error);
                        continue;
                }
                log.debug("Moving {} from {} to {}", next.queueItem, oldTarget, newTarget);
                try {
                    try {
                        Files.move(oldTarget.toPath(), newTarget.toPath(), StandardCopyOption.ATOMIC_MOVE);
                        log.debug("Setting state from {} back to {}", next.queueItem.getState(), next.queueItem.getOldState());
                        next.queueItem.setState(next.queueItem.getOldState());
                    } catch (AtomicMoveNotSupportedException ex) {

                        long oldSize = oldTarget.length();
                        long copied = 0;

                        long lastDisplay = System.currentTimeMillis();
                        try (InputStream in = new BufferedInputStream(new FileInputStream(oldTarget));
                                OutputStream out = new BufferedOutputStream(new FileOutputStream(newTarget))) {
                            byte[] buff = new byte[BUFFER_SIZE];
                            int read;
                            while ((read = in.read(buff)) != -1) {
                                out.write(buff, 0, read);

                                copied += read;
                                next.queueItem.setMoveProgress((float) copied / oldSize);
                                long now = System.currentTimeMillis();
                                if ((now - lastDisplay) > UPDATE_INTERVAL) {
                                    parent.notifyListDataListeners();
                                    lastDisplay = now;
                                }
                            }
                            out.flush();
                        }
                        if (!oldTarget.delete()) {
                            log.error("Can't delete old partial download {}: Unkown reason", oldTarget);
                        }
                        log.debug("Setting state from {} back to {}", next.queueItem.getState(), next.queueItem.getOldState());
                        next.queueItem.setState(next.queueItem.getOldState());
                    }
                } catch (IOException ex) {
                    log.error("Error: Rename from [{}] to [{}] failed: {}", oldTarget, newTarget, ex, ex.getMessage());
                    next.queueItem.setState(QueueItem.State.Error);
                }
                parent.notifyListDataListeners();
            }
        }

        private static class Pair {

            final QueueItem queueItem;
            File newPath;

            public Pair(QueueItem queueItem, File newPath) {
                super();
                this.queueItem = queueItem;
                this.newPath = newPath;
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 67 * hash + Objects.hashCode(this.queueItem);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Pair other = (Pair) obj;
                return Objects.equals(this.queueItem, other.queueItem);
            }
        }
    }
}
