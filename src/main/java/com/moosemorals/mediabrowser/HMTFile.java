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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mange the Humax data file format.
 * <p>
 * I didn't do any of the research for this. My main source is
 * <a href="https://myhumax.org/wiki/index.php?title=Humax_PVR_File_Formats">a
 * post on the 'My Humax' Wiki</a>, as well as
 * <a href="http://merrickchaffer.blogspot.co.uk/2012/11/how-to-remove-encryption-from-humax-hdr.html">a
 * blog post</a> that gives details of how to remove the lock.
 * <p>
 * This is the part of the code most likley to make your PVR catch fire.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class HMTFile {

    private final Logger log = LoggerFactory.getLogger(HMTFile.class);

    private final byte[] raw;

    private final Charset charset = Charset.forName("UTF8");

    public HMTFile(byte[] given) {
        raw = new byte[given.length];

        System.arraycopy(given, 0, raw, 0, given.length);
    }

    public String getDirectory() {
        return nullTerminated(0x0080, 512);
    }

    public String getRecordingFileName() {
        String result = nullTerminated(0x017F, 512);
        if (result.isEmpty()) {
            result = nullTerminated(0x0180, 512);
        }
        return result;
    }

    public void setRecordingFileName(String filename) {

        byte[] filenameBytes = filename.getBytes(charset);

        int offset = 0x17F;
        if (raw[offset] == 0) {
            offset = 0x0180;
        }

        int length = filenameBytes.length < 510 ? filenameBytes.length : 510;

        System.arraycopy(filenameBytes, 0, raw, offset, length);

        for (int i = length; i < 512; i += 1) {
            raw[offset + i] = (byte) 0;
        }
    }

    public String getRecordingTitle() {
        return nullTerminated(0x029A, 512);
    }

    public String getGuidanceDescription() {
        return nullTerminated(0x03E3, 77);
    }

    public String getChannelName() {
        return nullTerminated(0x045D, 10);
    }

    public String getTitle() {
        return nullTerminated(0x0517, 255);
    }

    public String getDesc() {
        return nullTerminated(0x0617, 255);
    }

    public long getStartTimestamp() {
        // http://stackoverflow.com/a/362390/195833

        long l = (long) raw[0x0280] & 0xFF;
        l += ((long) raw[0x0281] & 0xFF) << 8;
        l += ((long) raw[0x0282] & 0xFF) << 16;
        l += ((long) raw[0x0283] & 0xFF) << 24;

        return l;
    }

    public long getEndTimestamp() {
        // http://stackoverflow.com/a/362390/195833

        long l = (long) raw[0x0284] & 0xFF;
        l += ((long) raw[0x0285] & 0xFF) << 8;
        l += ((long) raw[0x0286] & 0xFF) << 16;
        l += ((long) raw[0x0287] & 0xFF) << 24;

        return l;
    }

    /**
     * Don't trust this value!
     *
     * @return
     */
    public long getLength() {
        // http://stackoverflow.com/a/362390/195833

        long l = (long) raw[0x04FC] & 0xFF;
        l += ((long) raw[0x04FD] & 0xFF) << 8;
        l += ((long) raw[0x04FE] & 0xFF) << 16;
        l += ((long) raw[0x04FF] & 0xFF) << 24;

        return l;
    }

    public boolean isHighDef() {
        return raw[0x04BC] == 0x02;
    }

    public boolean isLocked() {
        return raw[0x03DC] != 0x04;
    }

    public void clearLock() {
        raw[0x03DC] = 0x04;
    }

    public byte[] getBytes() {
        byte[] copy = new byte[raw.length];

        System.arraycopy(raw, 0, copy, 0, raw.length);
        return copy;
    }

    public void write(OutputStream out) throws IOException {
        out.write(raw);
    }

    private String nullTerminated(int offset, int length) {

        int i = 0;
        while (i < length && raw[offset + i] != 0) {
            i += 1;
        }

        String result = new String(raw, offset, i, charset);

        // I don't know whats up with this.
        if (result.startsWith("i7")) {
            return result.substring(2);
        } else {
            return result;
        }
    }

}
