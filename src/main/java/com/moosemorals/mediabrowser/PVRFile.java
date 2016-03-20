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

import java.io.File;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a file on the remote device.
 *
 */
public class PVRFile extends PVRItem {

    private final Logger log = LoggerFactory.getLogger(PVRFile.class);
    State state;
    boolean upnp = false;
    boolean ftp = false;
    long size = -1;
    long downloaded = -1;
    String remoteURL = null;
    String description = "";
    String title = "";
    String channelName = "Unknown";
    File localPath = null;
    String localFilename = null;
    DateTime startTime;
    DateTime endTime;
    Duration length;
    boolean highDef = false;
    boolean locked = false;

    protected PVRFile(PVRFolder parent, String path, String filename) {
        super(parent, path, filename);
        state = State.Ready;
        title = filename;
        localFilename = filename;
    }

    @Override
    public int compareTo(PVRItem o) {
        if (o.isFolder()) {
            // Folders go first
            return 1;
        } else {
            int x = getRemoteFilename().compareTo(o.getRemoteFilename());
            if (x == 0) {
                return startTime.compareTo(((PVRFile) o).startTime);
            } else {
                return x;
            }
        }
    }

    @Override
    public boolean isFolder() {
        return false;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public long getSize() {
        return size;
    }

    /**
     * Sets the queue state of this file.
     *
     * @param newState
     */
    void setState(State newState) {
        this.state = newState;
    }

    /**
     * Sets the human readable title of this file.
     *
     * @param title String human readable title.
     */
    void setTitle(String title) {
        this.title = title;
    }

    /**
     * Get the human readable title of this file.
     *
     * @return String human readable title.
     */
    public String getTitle() {
        return title;
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
    }

    /**
     * Get the current queue/download state.
     *
     * @return
     */
    public State getState() {
        return state;
    }

    /**
     * Set the size of this file, in bytes.
     *
     * @param size long size of file, in bytes.
     */
    void setSize(long size) {
        this.size = size;
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
    }

    /**
     * Get the human readable description of this file.
     *
     * @return String human readable description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Set the human readable description of this file.
     *
     * @param description
     */
    void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the remote URL this file can be downloaded from.
     *
     * @return String remote URL.
     */
    public String getRemoteURL() {
        return remoteURL;
    }

    /**
     * Set the remote URL to download this file.
     *
     * @param remoteURL String remote URL
     */
    void setRemoteURL(String remoteURL) {
        this.remoteURL = remoteURL;
    }

    /**
     * Get the start time of the recording. It should be in the PVRs timezone,
     * but that code hasn't really been written yet.
     *
     * @return DateTime start time of the recording
     */
    public DateTime getStartTime() {
        return startTime;
    }

    /**
     * Set the start time of the recording.
     *
     * @param startTime DateTime start time of the recording.
     */
    void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * Get the end time of the recording. See getStartTime() for Timezone
     * comments,
     *
     * @return DateTime start time of the recording.
     */
    public DateTime getEndTime() {
        return endTime;
    }

    /**
     * Set the end time of the recording.
     *
     * @param endTime
     */
    void setEndTime(DateTime endTime) {
        this.endTime = endTime;
    }

    /**
     * Get the length of the recording.
     *
     * @return Duration recording length.
     */
    public Duration getLength() {
        return length;
    }

    /**
     * Set the length of the recording.
     *
     * @param length Duration recording length
     */
    public void setLength(Duration length) {
        this.length = length;
    }

    /**
     * Is the recoding High Definition.
     *
     * Note that HD recordings can't be downloaded using this tool.
     *
     * @return boolean true if the recoding is HD, false otherwise.
     */
    public boolean isHighDef() {
        return highDef;
    }

    /**
     * Set the highdef flag. Setting this flag to true will not make a recording
     * high definition. Sorry.
     *
     * @param highDef boolean true for high def.
     */
    void setHighDef(boolean highDef) {
        this.highDef = highDef;
    }

    /**
     * Is the recording locked. Humax PVR sets a flag to say if a recording
     * should be decrypted when it's copied off the disk. This flag is ignored
     * (or at least, always set to decript) for SD files, but not for HD.
     *
     * @return boolean true if its a high def file, and can't be copied, false
     * otherwise.
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Set the locked flag. See {@link PVR.unlockFile(PVRFile)} if you actually
     * want to unlock a file.
     *
     * @param locked boolean true if locked, false otherwise.
     */
    void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * Get the channel the recording was made from.
     *
     * @return String channel name, or the String "Unknown" if not set.
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * Set the name of the channel.
     *
     * @param channelName String channel name.
     */
    void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    /**
     * Has been seen by Upnp
     *
     * @return true if has been seen.
     */
    public boolean isUpnpScanned() {
        return upnp;
    }

    /**
     * Set if has been seen by upnp
     *
     * @param upnp
     */
    void setUpnp(boolean upnp) {
        this.upnp = upnp;
    }

    /**
     * Has been seen by FTP
     *
     * @return true if seen by FTP
     */
    public boolean isFtpScanned() {
        return ftp;
    }

    void setFtp(boolean ftp) {
        this.ftp = ftp;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(title);
        result.append(": ");
        switch (state) {
            case Ready:
                result.append(PVR.humanReadableSize(size));
                break;
            case Queued:
                result.append(PVR.humanReadableSize(size)).append(" Queued");
                break;
            case Downloading:
                result.append(String.format("%s of %s (%3.0f%%) Downloading", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                break;
            case Paused:
                result.append(String.format("%s of %s (%3.0f%%) Paused", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                break;
            case Completed:
                result.append(PVR.humanReadableSize(downloaded)).append(" Completed");
                break;
            case Error:
                result.append(String.format("%s of %s (%3.0f%%) Broken", PVR.humanReadableSize(downloaded), PVR.humanReadableSize(size), (downloaded / (double) size) * 100.0));
                break;
        }
        return result.toString();
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
        Paused, /**
         * File has downloaded succesfully. At least, as far as we can tell....
         */
        Completed, /**
         * The file got broken somehow.
         */
        Error
    }

}
