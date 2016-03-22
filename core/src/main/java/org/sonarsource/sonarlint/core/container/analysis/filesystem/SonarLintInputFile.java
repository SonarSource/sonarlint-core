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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata.Metadata;

public class SonarLintInputFile extends DefaultInputFile {

  private final ClientInputFile clientInputFile;

  public SonarLintInputFile(ClientInputFile clientInputFile) {
    super(null, null);
    this.clientInputFile = clientInputFile;
  }

  public ClientInputFile getClientInputFile() {
    return clientInputFile;
  }

  @Override
  public String relativePath() {
    return absolutePath();
  }

  @Override
  public String absolutePath() {
    return PathUtils.sanitize(path().toString());
  }

  @Override
  public File file() {
    return path().toFile();
  }

  @Override
  public Path path() {
    return clientInputFile.getPath();
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
    return absolutePath();
  }

  @Override
  public String moduleKey() {
    throw unsupported();
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Unsupported in SonarLint");
  }

  @Override
  public Charset charset() {
    Charset charset = clientInputFile.getCharset();
    return charset != null ? charset : Charset.defaultCharset();
  }

  public SonarLintInputFile init(Metadata metadata) {
    this.setLines(metadata.lines);
    this.setLastValidOffset(metadata.lastValidOffset);
    this.setOriginalLineOffsets(metadata.originalLineOffsets);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    // Use instanceof to support DeprecatedDefaultInputFile
    if (!(o instanceof SonarLintInputFile)) {
      return false;
    }

    SonarLintInputFile that = (SonarLintInputFile) o;
    return path().equals(that.path());
  }

  @Override
  public int hashCode() {
    return path().hashCode();
  }

  @Override
  public String toString() {
    return "[path=" + path() + "]";
  }

  @Override
  public boolean isFile() {
    return true;
  }

}
