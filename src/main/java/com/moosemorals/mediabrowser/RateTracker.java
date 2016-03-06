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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keep track of a moving average of download speeds
 *
 * @author Osric Wilkinson <osric@fluffypeople.com>
 */
public class RateTracker {

    private final Logger log = LoggerFactory.getLogger(RateTracker.class);

    private final double[] rates;
    private int pointer = 0;
    boolean looped = false;

    public RateTracker(int size) {
        rates = new double[size];
    }

    public void addRate(double rate) {
        rates[pointer] = rate;
        pointer += 1;
        if (pointer >= rates.length) {
            looped = true;
            pointer = 0;
        }
    }

    public double getRate() {
        if (pointer == 0 && !looped) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < (looped ? rates.length : pointer); i += 1) {
            total += rates[i];
        }

        return total / (looped ? rates.length : pointer);
    }

    public void reset() {
        pointer = 0;
        looped = false;
    }
}
