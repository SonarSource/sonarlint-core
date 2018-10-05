/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextPointer;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.fs.internal.DefaultTextPointer;
import org.sonar.api.batch.fs.internal.DefaultTextRange;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata.Metadata;

public class SonarLintInputFile implements InputFile {

  private final ClientInputFile clientInputFile;
  private final String relativePath;
  private String language;
  private Type type;
  private Metadata metadata;
  private final Function<SonarLintInputFile, Metadata> metadataGenerator;

  public SonarLintInputFile(ClientInputFile clientInputFile, Function<SonarLintInputFile, Metadata> metadataGenerator) {
    this.clientInputFile = clientInputFile;
    this.metadataGenerator = metadataGenerator;
    this.relativePath = PathUtils.sanitize(clientInputFile.relativePath());
  }

  public void checkMetadata() {
    if (metadata == null) {
      this.metadata = metadataGenerator.apply(this);
    }
  }

  public ClientInputFile getClientInputFile() {
    return clientInputFile;
  }

  @Override
  public String relativePath() {
    return relativePath;
  }

  public void setLanguage(@Nullable String language) {
    this.language = language;
  }

  public void setType(Type type) {
    this.type = type;
  }

  @CheckForNull
  @Override
  public String language() {
    return language;
  }

  @Override
  public Type type() {
    return type;
  }

  /**
   * @deprecated avoid calling this method if possible, since it may require to create a temporary copy of the file
   */
  @Deprecated
  @Override
  public String absolutePath() {
    return PathUtils.sanitize(clientInputFile.getPath());
  }

  /**
   * @deprecated avoid calling this method if possible, since it may require to create a temporary copy of the file
   */
  @Deprecated
  @Override
  public File file() {
    return path().toFile();
  }

  /**
   * @deprecated avoid calling this method if possible, since it may require to create a temporary copy of the file
   */
  @Deprecated
  @Override
  public Path path() {
    return Paths.get(clientInputFile.getPath());
  }

  @Override
  public InputStream inputStream() throws IOException {
    return clientInputFile.inputStream();
  }

  @Override
  public String contents() throws IOException {
    return clientInputFile.contents();
  }

  @Override
  public Status status() {
    return Status.ADDED;
  }

  /**
   * Component key.
   */
  @Override
  public String key() {
    return uri().toString();
  }

  @Override
  public URI uri() {
    return clientInputFile.uri();
  }

  @Override
  public Charset charset() {
    Charset charset = clientInputFile.getCharset();
    return charset != null ? charset : Charset.defaultCharset();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SonarLintInputFile)) {
      return false;
    }

    SonarLintInputFile that = (SonarLintInputFile) o;
    return uri().equals(that.uri());
  }

  @Override
  public int hashCode() {
    return uri().hashCode();
  }

  @Override
  public String toString() {
    return "[uri=" + uri() + "]";
  }

  @Override
  public boolean isFile() {
    return true;
  }

  @Override
  public String filename() {
    return Paths.get(relativePath).getFileName().toString();
  }

  @Override
  public int lines() {
    checkMetadata();
    return metadata.lines;
  }

  @Override
  public boolean isEmpty() {
    checkMetadata();
    return metadata.lastValidOffset == 0;
  }

  @Override
  public TextPointer newPointer(int line, int lineOffset) {
    checkMetadata();
    return new DefaultTextPointer(line, lineOffset);
  }

  @Override
  public TextRange newRange(TextPointer start, TextPointer end) {
    checkMetadata();
    return newRangeValidPointers(start, end);
  }

  @Override
  public TextRange newRange(int startLine, int startLineOffset, int endLine, int endLineOffset) {
    checkMetadata();
    TextPointer start = newPointer(startLine, startLineOffset);
    TextPointer end = newPointer(endLine, endLineOffset);
    return newRangeValidPointers(start, end);
  }

  @Override
  public TextRange selectLine(int line) {
    checkMetadata();
    TextPointer startPointer = newPointer(line, 0);
    TextPointer endPointer = newPointer(line, lineLength(line));
    return newRangeValidPointers(startPointer, endPointer);
  }

  private static TextRange newRangeValidPointers(TextPointer start, TextPointer end) {
    return new DefaultTextRange(start, end);
  }

  private int lineLength(int line) {
    return lastValidGlobalOffsetForLine(line) - metadata.originalLineOffsets[line - 1];
  }

  private int lastValidGlobalOffsetForLine(int line) {
    return line < this.metadata.lines ? (metadata.originalLineOffsets[line] - 1) : metadata.lastValidOffset;
  }

}
