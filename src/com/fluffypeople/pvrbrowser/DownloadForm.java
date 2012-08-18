/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fluffypeople.pvrbrowser;

import java.awt.BorderLayout;
import java.awt.Container;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.teleal.cling.support.model.DIDLContent;
import org.teleal.cling.support.model.item.Item;

/**
 *
 * @author Osric
 */
public class DownloadForm implements Runnable {

    private static final Logger log = Logger.getLogger(DownloadForm.class);
    private final BlockingQueue<Item> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final HttpClient client;
    private final JProgressBar allDownloads;
    private final JProgressBar thisDownload;
    private final DefaultListModel downloadListModel;
    private final JList downloadList;

    private static File downloadFolder = null;

    public static void setDownloadFolder(File target) {
        downloadFolder = target;
    }

    public static File getDownloadFolder() {
        return downloadFolder;
    }

    public DownloadForm() {
        client = new DefaultHttpClient();

        allDownloads = new JProgressBar();
        thisDownload = new JProgressBar();
        thisDownload.setStringPainted(true);
        downloadListModel = new DefaultListModel();
        downloadList = new JList(downloadListModel);

        JFrame form = new JFrame("Download Progress");
        form.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container cp = form.getContentPane();

        cp.setLayout(new BorderLayout());
        cp.add(thisDownload, BorderLayout.SOUTH);

        cp.add(new JScrollPane(downloadList), BorderLayout.CENTER);

        form.pack();
        form.setVisible(true);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread t = new Thread(this, "Download");
            t.start();
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            Item next;
            try {
                next = queue.take();
            } catch (InterruptedException ex) {
                log.error("Unexpected Iterruption", ex);
                running.set(false);
                return;
            }

            log.debug("Downloading " + next.getTitle());
            try {
                final HttpGet request = new HttpGet(next.getFirstResource().getValue());
                final HttpResponse response = client.execute(request);

                final StatusLine result = response.getStatusLine();
                if (result.getStatusCode() != 200) {
                    log.error("Can't download item");
                    continue;
                }

                long len = Long.parseLong(response.getFirstHeader("Content-Length").getValue(), 10);


                final HttpEntity body = response.getEntity();

                log.debug("Downloading " + len + " bytes");

                if (len > 0) {
                    thisDownload.setMaximum((int)len);
                } else {
                    thisDownload.setIndeterminate(true);
                }

                final InputStream in = body.getContent();
                final File downloadTarget = new File(downloadFolder, next.getTitle());

                log.debug("Filename " + downloadTarget.getAbsolutePath());

                final OutputStream out = new FileOutputStream(downloadTarget);

                byte[] buffer = new byte[1024 * 4];
                long count = 0;
                int n = 0;
                while (-1 != (n = in.read(buffer))) {
                    out.write(buffer, 0, n);
                    count += n;
                    thisDownload.setValue((int)count);
                }

                in.close();
                out.close();

                thisDownload.setIndeterminate(false);
                thisDownload.setValue(thisDownload.getMaximum());

                downloadListModel.removeElement(next.getTitle());
            } catch (IOException ex) {
                log.error("IOExcption", ex);
            }
        }
    }

    public void addTarget(Item target) {
        downloadListModel.addElement(target.getTitle());
        allDownloads.setMaximum(downloadListModel.getSize());
        queue.add(target);
    }
}
