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

    private final FTPClient ftp;
    private final String hostname;

    public FTPRemote(String hostname) {
        this.hostname = hostname;
        FTPClientConfig config = new FTPClientConfig();
        config.setServerTimeZoneId("Europe/London");
        config.setServerLanguageCode("EN");

        ftp = new FTPClient();
        ftp.configure(config);
    }

    public void browse(DownloadManager dlManager) throws IOException {

        ftp.connect(hostname);
        int reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new IOException("FTP server refused connect");
        }
        if (!ftp.login("humaxftp", "0000")) {
            throw new IOException("Can't login to FTP");
        }

        List<String> queue = new ArrayList<>();

        queue.add("/");
        while (!queue.isEmpty()) {
            String target = queue.remove(0);

            if (!ftp.changeWorkingDirectory(target)) {
                throw new IOException("Can't change FTP directory to " + target);
            }

            for (FTPFile f : ftp.listFiles()) {
                if (f.getName().equals(".") || f.getName().equals("..")) {
                    // skip
                    continue;
                }
                if (f.isDirectory()) {
                    StringBuilder nextTarget = new StringBuilder();
                    nextTarget.append(target);
                    if (!target.endsWith("/")) {
                        nextTarget.append("/");
                    }
                    nextTarget.append(f.getName());

                    queue.add(nextTarget.toString());
                }

                log.debug("{} - {} - {}", target, f.getName(), f.getSize());
            }
        }

        ftp.disconnect();

    }

}
