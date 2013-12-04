/*
 * The MIT License
 * 
 * Copyright (c) 2013 Steven G. Brown
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
package hudson.plugins.timestamper.io;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.model.Run;
import hudson.plugins.timestamper.Timestamp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.SerializationUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.google.common.io.Closeables;
import com.google.common.primitives.Ints;

/**
 * Unit test for the {@link TimestampsReader} class.
 * 
 * @author Steven G. Brown
 */
@RunWith(Parameterized.class)
public class TimestampsReaderTest {

  /**
   * @return parameterised test data
   */
  @Parameters
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<Object[]>();
    for (int numToRead : Ints.asList(1, 2, 3, 10)) {
      parameters.add(new Object[] { false, numToRead });
      parameters.add(new Object[] { true, numToRead });
    }
    return parameters;
  }

  /**
   */
  @Parameter(0)
  public boolean serialize;

  /**
   */
  @Parameter(1)
  public int numToRead;

  /**
   */
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  private Run<?, ?> build;

  private TimestampsReader timestampsReader;

  /**
   * @throws Exception
   */
  @Before
  public void setUp() throws Exception {
    build = mock(Run.class);
    when(build.getRootDir()).thenReturn(folder.getRoot());
    timestampsReader = new TimestampsReader(build);
  }

  /**
   * @throws Exception
   */
  @Test
  public void testNoTimestampsToRead() throws Exception {
    assertThat(readTimestamps(), is(Collections.<Timestamp> emptyList()));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testReadFromStart() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    assertThat(readTimestamps(),
        is(Arrays.asList(t(1, 1), t(2, 2), t(3, 3), t(4, 4))));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testSkipZero() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    timestampsReader.skip(0);
    assertThat(readTimestamps(),
        is(Arrays.asList(t(1, 1), t(2, 2), t(3, 3), t(4, 4))));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testSkipOne() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    timestampsReader.skip(1);
    assertThat(readTimestamps(), is(Arrays.asList(t(2, 2), t(3, 3), t(4, 4))));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testSkipTwo() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    timestampsReader.skip(2);
    assertThat(readTimestamps(), is(Arrays.asList(t(3, 3), t(4, 4))));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testSkipToEnd() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    timestampsReader.skip(4);
    assertThat(readTimestamps(), is(Collections.<Timestamp> emptyList()));
  }

  /**
   * @throws Exception
   */
  @Test
  public void testSkipPastEnd() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1));
    timestampsReader.skip(5);
    assertThat(readTimestamps(), is(Collections.<Timestamp> emptyList()));
  }

  /**
   * Test that the time shifts file is read correctly. The time shifts file was
   * previously generated by this plug-in to record changes to the clock, i.e.
   * when {@link System#currentTimeMillis()} diverges from
   * {@link System#nanoTime()}. Newer versions of this plug-in no longer create
   * time shifts files due to JENKINS-19778.
   * 
   * @throws Exception
   */
  @Test
  public void testTimeShifts() throws Exception {
    writeTimestamps(Arrays.asList(1, 1, 1, 1, 20));
    writeTimeShifts(Arrays.asList(0, 10, 2, -10, 3, -10));
    assertThat(readTimestamps(),
        is(Arrays.asList(t(1, 10), t(2, 11), t(3, -10), t(4, -10), t(24, 10))));
  }

  private void writeTimestamps(List<Integer> timestampData) throws Exception {
    File timestamperDir = TimestampsWriter.timestamperDir(build);
    File timestampsFile = TimestampsWriter.timestampsFile(timestamperDir);
    writeToFile(timestampData, timestampsFile);
  }

  private void writeTimeShifts(List<Integer> timeShiftData) throws Exception {
    File timestamperDir = TimestampsWriter.timestamperDir(build);
    File timeShiftsFile = TimeShiftsReader.timeShiftsFile(timestamperDir);
    writeToFile(timeShiftData, timeShiftsFile);
  }

  private void writeToFile(List<Integer> data, File file) throws Exception {
    file.getParentFile().mkdirs();
    OutputStream outputStream = null;
    boolean threw = true;
    try {
      outputStream = new FileOutputStream(file, true);
      byte[] buffer = new byte[10];
      for (Integer value : data) {
        int len = Varint.write(value, buffer, 0);
        outputStream.write(buffer, 0, len);
      }
      threw = false;
    } finally {
      Closeables.close(outputStream, threw);
    }
  }

  private List<Timestamp> readTimestamps() throws Exception {
    List<Timestamp> timestamps = new ArrayList<Timestamp>();
    int iterations = 0;
    while (true) {
      if (serialize) {
        timestampsReader = (TimestampsReader) SerializationUtils
            .clone(timestampsReader);
      }
      Collection<Timestamp> next;
      if (numToRead == 1) {
        next = timestampsReader.read().asSet();
      } else {
        next = timestampsReader.read(numToRead);
      }
      if (next.isEmpty()) {
        return timestamps;
      }
      timestamps.addAll(next);
      iterations++;
      if (iterations > 10000) {
        throw new IllegalStateException(
            "time-stamps do not appear to terminate. read so far: "
                + timestamps);
      }
    }
  }

  private Timestamp t(long elapsedMillis, long millisSinceEpoch) {
    return new Timestamp(elapsedMillis, millisSinceEpoch);
  }
}
