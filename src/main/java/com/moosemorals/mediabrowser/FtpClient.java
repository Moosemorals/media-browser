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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class FtpClient implements Runnable {

    private static final String FTP_ROOT = "/My Video/";

    private final Logger log = LoggerFactory.getLogger(FtpClient.class);
    private final FTPClient ftp;
    private final AtomicBoolean ftpRunning;
    private final boolean debugFTP = false;
    private final PVR pvr;
    private final String remoteHostname;

    private Thread ftpThread = null;

    FtpClient(PVR pvr, String remoteHostname) {
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
            notifyBrowseListeners(DeviceListener.BrowseType.ftp, true);
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
            notifyBrowseListeners(DeviceListener.BrowseType.ftp, false);
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

    void scrapeFTP() throws IOException {
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
        List<PVRFolder> queue = new ArrayList<>();
        queue.add((PVRFolder) pvr.getRoot());
        while (!queue.isEmpty()) {
            PVRFolder directory = queue.remove(0);
            if (!ftp.changeWorkingDirectory(FTP_ROOT + directory.getRemotePath())) {
                throw new IOException("Can't change FTP directory to " + FTP_ROOT + directory.getRemotePath());
            }
            for (FTPFile f : ftp.listFiles()) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    // skip entries for this directory and parent directory
                    continue;
                }
                if (f.isDirectory()) {
                    PVRFolder next = pvr.addFolder(directory, f.getName());
                    queue.add(next);
                } else if (f.isFile() && f.getName().endsWith(".ts")) {
                    PVRFile file = pvr.addFile(directory, f.getName());
                    HMTFile hmt = getHMTForTs(file);
                    file.setSize(f.getSize());
                    file.setDescription(hmt.getDesc());
                    file.setTitle(hmt.getRecordingTitle());
                    file.setStartTime(new DateTime(hmt.getStartTimestamp() * 1000, PVR.DEFAULT_TIMEZONE));
                    file.setEndTime(new DateTime(hmt.getEndTimestamp() * 1000, PVR.DEFAULT_TIMEZONE));
                    file.setLength(new Duration(hmt.getLength() * 1000));
                    file.setHighDef(hmt.isHighDef());
                    file.setLocked(hmt.isLocked());
                    file.setChannelName(hmt.getChannelName());
                    file.setFtp(true);
                    file.setLocalFilename(String.format("%s - %s - [%s - Freeview - %s] UNEDITED", file.getTitle().replaceAll("[/?<>\\:*|\"^]", "_"), PVR.FILE_DATE_FORMAT.print(file.getStartTime()), file.isHighDef() ? "1920\u00d71080" : "SD", file.getChannelName()));
                }
            }
        }
        ftp.disconnect();
        log.info("Disconnected from FTP");
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
     * @param target
     * @throws IOException
     */
    public void unlockFile(PVRFile target) throws IOException {
        if (!target.getRemoteFilename().endsWith(".ts")) {
            throw new IllegalArgumentException("Target must be a .ts file: " + target.getRemoteFilename());
        }
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
        log.info("Uploading unlocked hmt file to {}/{}", target.getParent().getRemotePath(), uploadFilename);
        if (!ftp.storeFile(uploadFilename, new ByteArrayInputStream(hmt.getBytes()))) {
            throw new IOException("Can't upload unlocked hmt to " + uploadFilename);
        }
        ftp.disconnect();
        log.info("Disconnected from FTP");
    }

    HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getRemoteFilename().replaceAll("\\.ts$", ".hmt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (!ftp.retrieveFile(target, out)) {
            throw new IOException("Can't download " + file.getRemotePath() + ": Unknown reason");
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

    private void notifyBrowseListeners(DeviceListener.BrowseType type, boolean startStop) {
        synchronized (deviceListener) {
            for (DeviceListener l : deviceListener) {
                if (startStop) {
                    l.onBrowseBegin(type);
                } else {
                    l.onBrowseEnd(type);
                }
            }
        }
    }
}