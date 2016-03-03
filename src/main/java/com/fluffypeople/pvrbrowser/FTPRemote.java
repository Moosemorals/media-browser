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
package com.fluffypeople.pvrbrowser;

import com.fluffypeople.pvrbrowser.PVR.PVRFile;
import com.fluffypeople.pvrbrowser.PVR.PVRFolder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class FTPRemote {

    private final Logger log = LoggerFactory.getLogger(FTPRemote.class);

    private static final String BASE_DIR = "/My Video";

    private final FTPClient ftp;

    public FTPRemote() {
        FTPClientConfig config = new FTPClientConfig();
        config.setServerTimeZoneId("Europe/London");
        config.setServerLanguageCode("EN");

        ftp = new FTPClient();
        ftp.configure(config);
    }

    public void scrapeFTP(PVR pvr) throws IOException {
        ftp.connect(pvr.getHostname());
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

            if (!ftp.changeWorkingDirectory(BASE_DIR + directory.getPath())) {
                throw new IOException("Can't change FTP directory to " + directory);
            }

            for (FTPFile f : ftp.listFiles()) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    // skip
                    continue;
                }

                if (f.isDirectory()) {
                    PVRFolder next = pvr.addFolder(directory, f.getName());
                    queue.add(next);
                } else if (f.isFile() && f.getName().endsWith(".ts")) {

                    PVRFile file = pvr.addFile(directory, f.getName());

                    file.setSize(f.getSize());

                    HMTFile hmt = getHMTForTs(file);

                    file.setDescription(hmt.getDesc());

                }
            }
        }
        ftp.disconnect();
    }

    private HMTFile getHMTForTs(PVRFile file) throws IOException {
        String target = file.getName().replaceAll("\\.ts$", ".hmt");

        FTPFile[] listFiles = ftp.listFiles(target);
        if (listFiles.length != 1) {
            throw new IOException("Unexpected number of hmt files: " + listFiles.length);
        }

        FTPFile f = listFiles[0];

        if (f.getSize() > Integer.MAX_VALUE) {
            throw new IOException("Can't download " + file.getPath() + ": Bigger than MAX_INT");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.getSize());

        if (!ftp.retrieveFile(BASE_DIR + file.getPath(), out)) {
            throw new IOException("Can't download " + file.getPath() + ": Unknown reason");
        }

        return new HMTFile(out.toByteArray());
    }

}
