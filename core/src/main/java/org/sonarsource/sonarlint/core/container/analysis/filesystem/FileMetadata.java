/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import com.google.common.primitives.Ints;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

/**
 * Computes hash of files. Ends of Lines are ignored, so files with
 * same content but different EOL encoding have the same hash.
 */
@BatchSide
public class FileMetadata {

  private static final Logger LOG = Loggers.get(FileMetadata.class);

  private static final char LINE_FEED = '\n';
  private static final char CARRIAGE_RETURN = '\r';

  public abstract static class CharHandler {

    protected void handleAll(char c) {
    }

    protected void handleIgnoreEoL(char c) {
    }

    protected void newLine() {
    }

    protected void eof() {
    }
  }

  private static class LineCounter extends CharHandler {
    private int lines = 1;
    boolean alreadyLoggedInvalidCharacter = false;
    private final File file;
    private final Charset encoding;

    LineCounter(File file, Charset encoding) {
      this.file = file;
      this.encoding = encoding;
    }

    @Override
    protected void handleAll(char c) {
      if (!alreadyLoggedInvalidCharacter && c == '\ufffd') {
        LOG.warn("Invalid character encountered in file {} at line {} for encoding {}. Please fix file content or configure the encoding to be used using property '{}'.", file,
          lines, encoding, CoreProperties.ENCODING_PROPERTY);
        alreadyLoggedInvalidCharacter = true;
      }
    }

    @Override
    protected void newLine() {
      lines++;
    }

    public int lines() {
      return lines;
    }

  }

  private static class LineOffsetCounter extends CharHandler {
    private int currentOriginalOffset = 0;
    private List<Integer> originalLineOffsets = new ArrayList<>();
    private int lastValidOffset = 0;

    public LineOffsetCounter() {
      originalLineOffsets.add(0);
    }

    @Override
    protected void handleAll(char c) {
      currentOriginalOffset++;
    }

    @Override
    protected void newLine() {
      originalLineOffsets.add(currentOriginalOffset);
    }

    @Override
    protected void eof() {
      lastValidOffset = currentOriginalOffset;
    }

    public List<Integer> getOriginalLineOffsets() {
      return originalLineOffsets;
    }

    public int getLastValidOffset() {
      return lastValidOffset;
    }

  }

  /**
   * Compute hash of a file ignoring line ends differences.
   * Maximum performance is needed.
   */
  public Metadata readMetadata(File file, Charset encoding) {
    LineCounter lineCounter = new LineCounter(file, encoding);
    LineOffsetCounter lineOffsetCounter = new LineOffsetCounter();
    readFile(file, encoding, lineCounter, lineOffsetCounter);
    return new Metadata(lineCounter.lines(), lineOffsetCounter.getOriginalLineOffsets(), lineOffsetCounter.getLastValidOffset());
  }

  /**
   * For testing purpose
   */
  public Metadata readMetadata(Reader reader) {
    LineCounter lineCounter = new LineCounter(new File("fromString"), StandardCharsets.UTF_16);
    LineOffsetCounter lineOffsetCounter = new LineOffsetCounter();
    try {
      read(reader, lineCounter, lineOffsetCounter);
    } catch (IOException e) {
      throw new IllegalStateException("Should never occurs", e);
    }
    return new Metadata(lineCounter.lines(), lineOffsetCounter.getOriginalLineOffsets(), lineOffsetCounter.getLastValidOffset());
  }

  public static void readFile(File file, Charset encoding, CharHandler... handlers) {
    try (BOMInputStream bomIn = new BOMInputStream(new FileInputStream(file),
      ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE);
      Reader reader = new BufferedReader(new InputStreamReader(bomIn, encoding))) {
      read(reader, handlers);
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Fail to read file '%s' with encoding '%s'", file.getAbsolutePath(), encoding), e);
    }
  }

  private static void read(Reader reader, CharHandler... handlers) throws IOException {
    char c;
    int i = reader.read();
    boolean afterCR = false;
    while (i != -1) {
      c = (char) i;
      if (afterCR) {
        for (CharHandler handler : handlers) {
          if (c == CARRIAGE_RETURN) {
            handler.newLine();
            handler.handleAll(c);
          } else if (c == LINE_FEED) {
            handler.handleAll(c);
            handler.newLine();
          } else {
            handler.newLine();
            handler.handleIgnoreEoL(c);
            handler.handleAll(c);
          }
        }
        afterCR = c == CARRIAGE_RETURN;
      } else if (c == LINE_FEED) {
        for (CharHandler handler : handlers) {
          handler.handleAll(c);
          handler.newLine();
        }
      } else if (c == CARRIAGE_RETURN) {
        afterCR = true;
        for (CharHandler handler : handlers) {
          handler.handleAll(c);
        }
      } else {
        for (CharHandler handler : handlers) {
          handler.handleIgnoreEoL(c);
          handler.handleAll(c);
        }
      }
      i = reader.read();
    }
    for (CharHandler handler : handlers) {
      if (afterCR) {
        handler.newLine();
      }
      handler.eof();
    }
  }

  public static class Metadata {
    final int lines;
    final int[] originalLineOffsets;
    final int lastValidOffset;

    private Metadata(int lines, List<Integer> originalLineOffsets, int lastValidOffset) {
      this.lines = lines;
      this.originalLineOffsets = Ints.toArray(originalLineOffsets);
      this.lastValidOffset = lastValidOffset;
    }
  }
}
