/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

/**
 * Computes hash of files. Ends of Lines are ignored, so files with
 * same content but different EOL encoding have the same hash.
 */
@SonarLintSide
public class FileMetadata {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

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
    private final URI fileUri;
    private final Charset encoding;

    LineCounter(URI fileUri, Charset encoding) {
      this.fileUri = fileUri;
      this.encoding = encoding;
    }

    @Override
    protected void handleAll(char c) {
      if (!alreadyLoggedInvalidCharacter && c == '\ufffd') {
        LOG.warn("Invalid character encountered in file '{}' at line {} for encoding {}. Please fix file content or configure the encoding.",
          fileUri,
          lines, encoding);
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
    private final List<Integer> originalLineOffsets = new ArrayList<>();
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
   * For testing
   */
  Metadata readMetadata(File file, Charset encoding) {
    var stream = streamFile(file);
    return readMetadata(stream, encoding, file.toURI(), null);
  }

  /**
   * Compute hash of an inputStream ignoring line ends differences.
   * Maximum performance is needed.
   */
  public Metadata readMetadata(InputStream stream, Charset encoding, URI fileUri, @Nullable CharHandler otherHandler) {
    var lineCounter = new LineCounter(fileUri, encoding);
    var lineOffsetCounter = new LineOffsetCounter();
    try (Reader reader = new BufferedReader(new InputStreamReader(stream, encoding))) {
      CharHandler[] handlers;
      if (otherHandler != null) {
        handlers = new CharHandler[] {lineCounter, lineOffsetCounter, otherHandler};
      } else {
        handlers = new CharHandler[] {lineCounter, lineOffsetCounter};
      }
      read(reader, handlers);
    } catch (IOException e) {
      throw new IllegalStateException(String.format("Fail to read file '%s' with encoding '%s'", fileUri, encoding), e);
    }
    return new Metadata(lineCounter.lines(), lineOffsetCounter.getOriginalLineOffsets().stream().mapToInt(i -> i).toArray(), lineOffsetCounter.getLastValidOffset());
  }

  private static InputStream streamFile(File file) {
    try {
      return new BOMInputStream(new FileInputStream(file),
        ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("File not found: " + file.getAbsolutePath(), e);
    }
  }

  private static void read(Reader reader, CharHandler... handlers) throws IOException {
    char c;
    var i = reader.read();
    var afterCR = false;
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
    private final int lines;
    private final int[] originalLineOffsets;
    private final int lastValidOffset;

    public Metadata(int lines, int[] originalLineOffsets, int lastValidOffset) {
      this.lines = lines;
      this.originalLineOffsets = originalLineOffsets;
      this.lastValidOffset = lastValidOffset;
    }

    public int lines() {
      return lines;
    }

    public int[] originalLineOffsets() {
      return originalLineOffsets;
    }

    public int lastValidOffset() {
      return lastValidOffset;
    }
  }
}
