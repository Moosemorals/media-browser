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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class HMTFileNGTest {

    private final Logger log = LoggerFactory.getLogger(HMTFileNGTest.class);

    public HMTFileNGTest() {
    }

    @Test
    public void testGetDescription1() throws Exception {
        HMTFile f = new HMTFile(loadTestFile("/test01.hmt"));
        assertEquals(f.getSynopsis(), "Star Trek spin-off taking place on a space station orbiting the planet Bajor. Things go bad on a visit to Risa, as rebels take control of the planets’ weather regulator.");
    }

    @Test
    public void testGetDescription2() throws Exception {
        HMTFile f = new HMTFile(loadTestFile("/test02.hmt"));
        assertEquals(f.getSynopsis(), "Offbeat satire. A ballroom-dancing sensation causes uproar among the traditionalist Australian Dance Federation when he tries out some new routines. Contains adult humour.  [HD] [1992] [S]");
    }

    @Test
    public void testGetGenra1() throws Exception {
        HMTFile f = new HMTFile(loadTestFile("/test01.hmt"));
        assertEquals(f.getGenre(), HMTFile.Genre.Entertainment);
    }

    @Test
    public void testGetGenra2() throws Exception {
        HMTFile f = new HMTFile(loadTestFile("/test02.hmt"));
        assertEquals(f.getGenre(), HMTFile.Genre.Movie);
    }

    @Test
    public void testGetGenra3() throws Exception {
        HMTFile f = new HMTFile(loadTestFile("/test03.hmt"));
        log.debug("Name: {}", f.getProgramName());
        assertEquals(f.getGenre(), HMTFile.Genre.NewsAndFactual);
    }

    private static byte[] loadTestFile(String name) throws IOException {

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); InputStream in = HMTFileNGTest.class.getResourceAsStream(name)) {

            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer, 0, 4096)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return out.toByteArray();
        }
    }

}
