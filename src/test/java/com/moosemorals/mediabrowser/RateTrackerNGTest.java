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

import static junit.framework.Assert.assertEquals;
import org.testng.annotations.Test;

/**
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class RateTrackerNGTest {

    private static final double DELTA = 0.0001;

    public RateTrackerNGTest() {
    }

    @Test
    public void test_one_value() {
        RateTracker t = new RateTracker(1);
        // One value, should get the same value back
        t.addRate(1);
        assertEquals(1, t.getRate(), DELTA);
    }

    @Test
    public void test_two_values() {
        RateTracker t = new RateTracker(2);

        // Two values, two slots, should get average back
        t.addRate(1);
        t.addRate(2);
        assertEquals(1.5, t.getRate(), DELTA);
    }

    @Test
    public void test_loop() {
        RateTracker t = new RateTracker(2);

        // THree values, two slots, should get average of second two values
        t.addRate(1);
        t.addRate(2);
        t.addRate(3);
        assertEquals(2.5, t.getRate(), DELTA);
    }

    @Test
    public void test_space() {
        RateTracker t = new RateTracker(10);

        // three values, ten slots, should get average of three values
        t.addRate(1);
        t.addRate(2);
        t.addRate(3);
        assertEquals(2, t.getRate(), DELTA);
    }

    @Test
    public void test_reset() {
        RateTracker t = new RateTracker(2);

        // add two values, clear, add a value, should get that value back
        t.addRate(5);
        t.addRate(5);
        t.reset();
        t.addRate(1);
        assertEquals(1, t.getRate(), DELTA);
    }

}
