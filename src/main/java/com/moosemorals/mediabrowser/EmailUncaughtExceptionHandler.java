/*
 * The MIT License
 *
 * Copyright 2016 Osric Wilkinson (osric@fluffypeople.com).
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 *
 * @author Osric Wilkinson (osric@fluffypeople.com)
 */
public class EmailUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        String user = System.getProperty("user.name");
        String hostname;
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostname = localhost.getCanonicalHostName();
        } catch (UnknownHostException nested) {
            hostname = user + ".localhost";
        }
        
        String subject = ex.getMessage(); 
        if (subject == null) {
            subject = "";
        }
        
        String from =  user + "@" + hostname;
        String error = "Exception in thread: " + thread.getName() + "\n" + renderThrowable(ex);
        sendMessage(from, subject, error);
    }

    public static String renderThrowable(Throwable ex) {
        Throwable cause = ex;
        List<Throwable> causes = new LinkedList<>();
        while (cause != null) {
            causes.add(cause);
            cause = cause.getCause();
        }
        Collections.reverse(causes);

        StringBuilder message = new StringBuilder();

        for (int i = 0; i < causes.size(); i += 1) {
            cause = causes.get(i);

            if (i != 0) {
                message.append("wrapped by ");
            }
            message.append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                message.append(": ");
                message.append(cause.getMessage());
            }
            message.append("\n");

            if (i != 0) {
                StackTraceElement[] thisStack = cause.getStackTrace();
                StackTraceElement[] prevStack = causes.get(i - 1).getStackTrace();

                int thisDepth = thisStack.length - 1;
                int prevDepth = prevStack.length - 1;

                while (thisDepth >= 0 && prevDepth >= 0 && thisStack[thisDepth].equals(prevStack[prevDepth])) {
                    thisDepth -= 1;
                    prevDepth -= 1;
                }

                int framesInCommon = thisStack.length - 1 - thisDepth;

                for (int j = 0; j <= thisDepth; j += 1) {
                    message.append(renderElement(thisStack[j]));
                }
                
                if (framesInCommon != 0) {
                    message.append("  then same as before\n");
                }
            } else {
                for (StackTraceElement e : cause.getStackTrace()) {
                    message.append(renderElement(e));
                }
            }

        }
        return message.toString();
    }

    private static String renderElement(StackTraceElement e) {
        StringBuilder result = new StringBuilder();
        result.append("  at ");
        result.append(e.getClassName());
        result.append(".");
        result.append(e.getMethodName());
        result.append(" (");
        if (!e.isNativeMethod()) {
            result.append(e.getFileName());
            result.append(":");
            result.append(e.getLineNumber());
        } else {
            result.append("<native>");
        }
        result.append(")\n");
        return result.toString();
    }

    private void sendMessage(String from, String subject, String text) {

        // Recipient's email ID needs to be mentioned.
        String to = "osric@fluffypeople.com";

        // Assuming you are sending email from localhost
        // Get system properties
        Properties properties = System.getProperties();

        // Setup mail server
        properties.setProperty("mail.smtp.host", "fluffypeople.com");
        properties.setProperty("mail.smtp.port", "25");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.trust", "fluffypeople.com");

        // Get the default Session object.
        Session session = Session.getDefaultInstance(properties);
        session.setDebug(true);

        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Set Subject: header field
            message.setSubject("[Media Downloader Error]: " + subject);

            // Now set the actual message
            message.setText(text);

            // Send message
            Transport.send(message);
            System.out.println("Sent message successfully....");
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }
}
