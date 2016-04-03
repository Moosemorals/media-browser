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

import static com.moosemorals.mediabrowser.PVR.DEFAULT_TIMEZONE;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class FtpScanner implements Runnable {

    private static final String FTP_ROOT = "/mnt/hd2/My Video";

    private final Logger log = LoggerFactory.getLogger(FtpScanner.class);
    private final FTPClient ftp;
    private final AtomicBoolean ftpRunning;
    private final boolean debugFTP = false;
    private final PVR pvr;
    private final String remoteHostname;

    private Thread ftpThread = null;

    FtpScanner(PVR pvr, String remoteHostname) {
        this.pvr = pvr;
        this.remoteHostname = remoteHostname;

        FTPClientConfig config = new FTPClientConfig();
        config.setServerTimeZoneId(DEFAULT_TIMEZONE.getID());
        config.setServerLanguageCode("EN");

        ftp = new FTPClient();
        ftp.configure(config);
        if (debugFTP) {
            ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true));
        }
        ftpRunning = new AtomicBoolean(false);

    }

    void start() {
        if (ftpRunning.compareAndSet(false, true)) {
            ftpThread = new Thread(this, "FTP");
            ftpThread.start();
            notifyScanListeners(DeviceListener.ScanType.ftp, true);
        }
    }

    @Override
    public void run() {
        try {
            scrapeFTP();
        } catch (IOException ex) {
            log.error("FTP problem: {}", ex.getMessage(), ex);
            stop();
        } finally {
            notifyScanListeners(DeviceListener.ScanType.ftp, false);
            ftpRunning.set(false);
        }
    }

    void stop() {
        if (ftpRunning.compareAndSet(true, false) && ftpThread != null) {
            ftpThread.interrupt();
            try {
                log.info("Waitng for ftpThread to finish");
                ftpThread.join();

            } catch (InterruptedException ex) {
                log.error("Unexpected interruption waiting for ftpThread to finish");
            }
        }
    }

    /**
     * Unset the locked flag.
     *
     * <p>
     * This is one of the two core operations of this project. It connects to
     * the PVR using FTP, fetches the PVR's control file, sets the value of a
     * significant byte and uploads the file back to the PVR, over-writing the
     * existing file.</p>
     *
     * <p>
     * Using this method may be illegal in certain jurisdictions. Seriously. See
     * <a href="https://en.wikipedia.org/wiki/Anti-circumvention">this Wikipedia
     * article</a> to start with, and then talk to a lawyer.</p>
     *
     * @param targets List of PVRFiles to unlock
     * @throws IOException
     */
    public void unlockFile(List<PVRFile> targets) throws IOException {
        synchronized (ftp) {
            boolean connected = connect();
            for (PVRFile target : targets) {

                if (!target.isLocked()) {
                    continue;
                }

                if (!target.getRemoteFilename().endsWith(".ts")) {
                    throw new IllegalArgumentException("Target must be a .ts file: " + target.getRemoteFilename());
                }

                if (!ftp.changeWorkingDirectory(FTP_ROOT + target.getParent().getRemotePath())) {
                    throw new IOException("Can't change FTP directory to " + FTP_ROOT + target.getParent().getRemotePath());
                }
                HMTFile hmt = getHMTForTs(target);
                if (!hmt.isLocked()) {
                    log.info("Unlock failed: {} is already unlocked", target.getRemoteFilename());
                    return;
                }

                hmt.clearLock();

                String uploadFilename = target.getRemoteFilename().replaceAll("\\.ts$", ".hmt");
                log.info("Uploading unlocked hmt file to {}{}", target.getParent().getRemotePath(), uploadFilename);

                if (!ftp.storeFile(uploadFilename, new ByteArrayInputStream(hmt.getBytes()))) {
                    throw new IOException("Can't upload unlocked hmt to " + uploadFilename);
                }

                renameInPlace(target);
                updateFromHMT(target);
                pvr.updateItem(target);
            }

            if (connected) {
                disconnect();
            }

        }

    }

    /**
     * Triggers a DLNA server rescan (on the PVR). Skips a lot of checks on the
     * assumtion that its called from unlock only.
     *
     * @param target
     */
    private void renameInPlace(PVRFile target) throws IOException {

        String basename = FilenameUtils.getBaseName(target.getRemoteFilename());

        for (FTPFile f : ftp.listFiles()) {
            String oldName = f.getName();
            if (basename.equals(FilenameUtils.getBaseName(oldName))) {

                String extension = FilenameUtils.getExtension(oldName);

                String newName;
                if (basename.endsWith("-")) {
                    newName = basename.substring(0, basename.length() - 1) + extension;
                } else {
                    newName = basename + "-." + extension;
                }

                log.debug("Moving {} to {} ", oldName, newName);

                if (!ftp.rename(oldName, newName)) {
                    throw new IOException("Can't rename " + target.getRemoteFilename());
                }

                if (extension.equals("ts")) {
                    target.setRemoteFilename(newName);
                }

            }
        }

    }

    public void renameFile(List<PVRFile> targets) throws IOException {
        synchronized (ftp) {
            boolean connected = connect();

            for (PVRFile target : targets) {
                if (!ftp.changeWorkingDirectory(FTP_ROOT + target.getParent().getRemotePath())) {
                    throw new IOException("Can't change FTP directory to " + FTP_ROOT + target.getParent().getRemotePath());
                }

                pvr.updateItem(target);
            }

            if (connected) {
                disconnect();
            }
        }
    }

    /**
     * Connect to the ftp server.
     *
     * @return boolean true if we needed to connect, false for already
     * connected.
     * @throws IOException
     */
    private boolean connect() throws IOException {
        if (!ftp.isConnected()) {
            log.info("Connecting to FTP");
            ftp.connect(remoteHostname);
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new IOException("FTP server refused connect");
            }
            if (!ftp.login("humaxftp", "0000")) {
                throw new IOException("Can't login to FTP");
            }
            if (!ftp.setFileType(FTPClient.BINARY_FILE_TYPE)) {
                throw new IOException("Can't set binary transfer");
            }
            return true;
        } else {
            return false;
        }
    }

    private void disconnect() throws IOException {
        if (ftp.isConnected()) {
            ftp.disconnect();
            log.info("Disconnected from FTP");
        }
    }

    private void scrapeFTP() throws IOException {
        synchronized (ftp) {
            connect();
            List<PVRFolder> queue = new LinkedList<>();
            queue.add((PVRFolder) pvr.getRoot());

            int total = 0;
            int checked = 0;

            while (!queue.isEmpty() && !ftpThread.isInterrupted()) {
                PVRFolder directory = queue.remove(0);
                if (!ftp.changeWorkingDirectory(FTP_ROOT + directory.getRemotePath())) {
                    throw new IOException("Can't change FTP directory to " + FTP_ROOT + directory.getRemotePath());
                }

                FTPFile[] fileList = ftp.listFiles();
                total += fileList.length;
                for (FTPFile f : fileList) {
                    if (f.getName().equals(".") || f.getName().equals("..")) {
                        // skip entries for this directory and parent directory
                        total -= 1;
                        continue;
                    }
                    if (f.isDirectory()) {
                        PVRFolder next = pvr.addFolder(directory, f.getName());
                        pvr.updateItem(next);
                        queue.add(next);
                    } else if (f.isFile() && f.getName().endsWith(".ts")) {
                        PVRFile file = pvr.addFile(directory, f.getName());
                        file.setSize(f.getSize());
                        updateFromHMT(file);
                        pvr.updateItem(file);
                    }
                    checked += 1;
                    notifyScanListeners(DeviceListener.ScanType.ftp, total, checked);
                }
            }
            disconnect();
        }
    }

    private void updateFromHMT(PVRFile file) throws IOException {
        synchronized (ftp) {
            HMTFile hmt = getHMTForTs(file);

            file.setDescription(hmt.getDesc());
            file.setTitle(hmt.getRecordingTitle());
            file.setStartTime(new DateTime(hmt.getStartTimestamp() * 1000, PVR.DEFAULT_TIMEZONE));
            file.setEndTime(new DateTime(hmt.getEndTimestamp() * 1000, PVR.DEFAULT_TIMEZONE));
            file.setLength(new Duration(hmt.getLength() * 1000));
            file.setHighDef(hmt.isHighDef());
            file.setLocked(hmt.isLocked());
            file.setChannelName(hmt.getChannelName());
            file.setFtp(true);
        }
    }

    private HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getRemoteFilename().replaceAll("\\.ts$", ".hmt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String remotePath = FTP_ROOT + file.getParent().getRemotePath();
        if (remotePath.endsWith("/")) {
            remotePath = remotePath.substring(0, remotePath.length() - 1);
        }
        if (!remotePath.equals(ftp.printWorkingDirectory())) {
            if (!ftp.changeWorkingDirectory(remotePath)) {
                throw new IOException("Can't change to directory " + remotePath);
            }
        }

        if (!ftp.retrieveFile(target, out)) {
            throw new IOException("Can't download " + target + ": Unknown reason");
        }
        return new HMTFile(out.toByteArray());
    }

    private final Set<DeviceListener> deviceListener = new HashSet<>();

    public void addDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.add(l);
        }
    }

    public void removeDeviceListener(DeviceListener l) {
        synchronized (deviceListener) {
            deviceListener.remove(l);
        }
    }

    private void notifyScanListeners(DeviceListener.ScanType type, boolean startStop) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (startStop) {
                    l.onScanStart(type);
                } else {
                    l.onScanComplete(type);
                }
            }
        }
    }

    private void notifyScanListeners(DeviceListener.ScanType type, int total, int completed) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                l.onScanProgress(type, total, completed);
            }
        }
    }

}
