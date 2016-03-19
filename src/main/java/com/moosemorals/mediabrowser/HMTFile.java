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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * blog post</a> that gives details of how to remove the lock.</p>
 * <p>
 * Also
 * <a href="https://gist.github.com/GrahamCobb/0e8c854eb75e5b00f353">https://gist.github.com/GrahamCobb/0e8c854eb75e5b00f353</a>
 * seams to have most complete/up-to-date information.</p>
 * <p>
 * This is the part of the code most likley to make your PVR catch fire.
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class HMTFile {

    private static final int VIEWED_FLAG = 0x08;

    private final Logger log = LoggerFactory.getLogger(HMTFile.class);

    private final byte[] raw;

    private static final Charset DEFAULT_CHARSET = Charset.forName("ISO-6937");

    public HMTFile(byte[] given) {
        raw = new byte[given.length];

        System.arraycopy(given, 0, raw, 0, given.length);
    }

    public boolean isViewed() {
        return (raw[0x028d] & VIEWED_FLAG) == VIEWED_FLAG;
    }

    public int getBookmarkCount() {
        return raw[0x0290] & 0xff;
    }

    public RecordingState getRecordingState() {
        switch (raw[0x028C]) {
            case 0x00:
                return RecordingState.ZeroLength;
            case 0x02:
                return RecordingState.Valid;
            case 0x03:
                return RecordingState.Scrambled;
            case 0x04:
                return RecordingState.Failed;
            default:
                return RecordingState.PowerLoss;
        }
    }

    public Genre getGenre() {

        int genreCode = raw[0x512] & 0xff;
        if (genreCode == 0) {
            genreCode = raw[0x514] & 0xff;
        }

        switch (genreCode) {
            case 0x10:
                return Genre.Movie;
            case 0x20:
            case 0x70:
            case 0x80:
                return Genre.NewsAndFactual;
            case 0x30:
            case 0x60:
                return Genre.Entertainment;
            case 0x40:
                return Genre.Sport;
            case 0x50:
                return Genre.Childrens;
            case 0x90:
                return Genre.Education;
            case 0xA0:
                return Genre.Lifestyle;
            case 0xF0:
                return Genre.Drama;
            default:
                return Genre.Unclassified;

        }
    }

    public String getDirectory() {
        return getStringDefaultCharset(0x0080, 512);
    }

    public String getRecordingFileName() {
        return getStringDefaultCharset(0x017F, 512);
    }

    public String getRecordingTitle() {
        return getStringDefaultCharset(0x029A, 512);
    }

    public String getGuidanceDescription() {
        return getStringDefaultCharset(0x03E3, 77);
    }

    public String getChannelName() {
        return getStringWithCharset(0x045C, 10);
    }

    public String getProgramName() {
        return getStringWithCharset(0x0516, 255);
    }

    public String getSynopsis() {
        return getStringWithCharset(0x0616, 255);
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

    private String getStringWithCharset(int offset, int length) {
        int encType = raw[offset++];

        Charset charset;
        switch (encType) {
            case 0x01:
                charset = Charset.forName("ISO8859_5");
                break;
            case 0x02:
                charset = Charset.forName("ISO8859_6");
                break;
            case 0x03:
                charset = Charset.forName("ISO8859_7");
                break;
            case 0x04:
                charset = Charset.forName("ISO8859_8");
                break;
            case 0x05:
                charset = Charset.forName("ISO8859_9");
                break;
            case 0x10:
                charset = DEFAULT_CHARSET;

                offset += 2;
                break;
            case 0x11:
                charset = Charset.forName("UTF-16");
                break;
            default:
                charset = DEFAULT_CHARSET;
                break;
        }

        int i = 0;
        while (i < length && raw[offset + i] != 0) {
            i += 1;
        }

        String s = new String(raw, offset, i, charset);
        return s;
    }

    private String getStringDefaultCharset(int offset, int length) {
        int i = 0;
        while (i < length && raw[offset + i] != 0) {
            i += 1;
        }

        return new String(raw, offset, i, Charset.forName("ISO8859_1"));

    }

    private int twoBytesToInt(int offset) {
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put(raw[offset]);
        bb.put(raw[offset + 1]);
        return bb.getShort(0) & 0xffff;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private String bytesToHex(int offset, int length) {
        char[] hexChars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int v = raw[offset + j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public enum RecordingState {
        Valid, ZeroLength, PowerLoss, Scrambled, Failed;

        @Override
        public String toString() {
            switch (this) {
                case Valid:
                    return "Valid";
                case ZeroLength:
                    return "Zero Length";
                case PowerLoss:
                    return "Power Loss";
                case Scrambled:
                    return "Scrambled";
                case Failed:
                    return "Failed";
                default:
                    throw new IllegalArgumentException("Unknown recording state " + this.name());
            }
        }
    }

    public enum VideoType {
        MPEG2, H264, Unknown
    }

    public enum AudioType {
        MPEG, AC3, AAC, Unknown
    }

    public enum Genre {
        Unclassified, Movie, NewsAndFactual, Entertainment,
        Sport, Childrens, Education, Lifestyle, Drama;

        @Override
        public String toString() {
            switch (this) {
                default:
                    return "Unclassified";
                case Movie:
                    return "Movie";
                case NewsAndFactual:
                    return "News and Facutal";
                case Entertainment:
                    return "Entertainment";
                case Sport:
                    return "Sport";
                case Childrens:
                    return "Childrens'";
                case Education:
                    return "Education";
                case Lifestyle:
                    return "Lifestyle";
                case Drama:
                    return "Drama";
            }
        }
    }

}
