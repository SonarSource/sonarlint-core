/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.internal.DefaultIndexedFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.filesystem.FileMetadata.Metadata;

public class SonarLintInputFile extends DefaultInputFile {

  private final ClientInputFile clientInputFile;
  private String language;
  private Type type;

  public SonarLintInputFile(ClientInputFile clientInputFile) {
    super(new DefaultIndexedFile(SonarLintInputModule.SONARLINT_FAKE_MODULE_KEY, Paths.get(clientInputFile.getPath()), clientInputFile.getPath(), clientInputFile.language()),
      null);
    this.clientInputFile = clientInputFile;
  }

  public ClientInputFile getClientInputFile() {
    return clientInputFile;
  }

  @Override
  public String relativePath() {
    return PathUtils.sanitize(clientInputFile.relativePath());
  }

  public void setLanguage(String language) {
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
    return absolutePath();
  }

  @Override
  public String moduleKey() {
    throw unsupported();
  }

  @Override
  public URI uri() {
    return clientInputFile.uri();
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
    this.setMetadata(new org.sonar.api.batch.fs.internal.Metadata(
      metadata.lines, metadata.lines, "", metadata.originalLineOffsets, metadata.lastValidOffset));
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
