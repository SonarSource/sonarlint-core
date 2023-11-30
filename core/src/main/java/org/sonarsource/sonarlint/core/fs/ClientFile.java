/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.fs;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;

public class ClientFile {

  /**
   * Unique identifier for this file on the client side
   */
  private final URI uri;

  private final String configScopeId;

  /**
   * Relative path for this file on the client side. We use the {@link Path} class for convenience for filename separators,
   * but it is not necessary to have a file on the filesystem.
   */
  private Path relativePath;

  /**
   * For some clients, deciding if a file is a test is costly, and will be computed only when a file is opened in an editor.
   * null means unknown
   */
  @Nullable
  private Boolean isTest;

  @Nullable
  private Charset charset;

  /**
   * The absolute path on the local filesystem, if available.
   */
  @Nullable
  private Path fsPath;

  /**
   * Tell if the file content is flushed on disk?
   * If the file is dirty, it means that the content is not flushed on disk, and the backend get the content from the client.
   */
  private boolean isDirty;

  /**
   * When the file is dirty, the content should be provided by the client.
   */
  @Nullable
  private String clientProvidedContent;

  public ClientFile(URI uri, String configScopeId, Path relativePath, @Nullable Boolean isTest, @Nullable Charset charset, @Nullable Path fsPath) {
    this.uri = uri;
    this.configScopeId = configScopeId;
    this.relativePath = relativePath;
    this.isTest = isTest;
    this.charset = charset;
    this.fsPath = fsPath;
  }

  public Path getClientRelativePath() {
    return relativePath;
  }

  public String getFileName() {
    return relativePath.getFileName().toString();
  }

  public URI getUri() {
    return uri;
  }

  public boolean isDirty() {
    return isDirty;
  }

  public String getContent() {
    if (isDirty) {
      return clientProvidedContent;
    }
    if (fsPath == null) {
      throw new IllegalStateException("File " + uri + " is not dirty but has no OS Path defined");
    }
    var charsetToUse = charset != null ? charset : Charset.defaultCharset();
    try {
      return Files.readString(fsPath, charsetToUse);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read file " + fsPath + "content with charset " + charsetToUse, e);
    }
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public void setDirty(String content) {
    this.isDirty = true;
    this.clientProvidedContent = content;
  }

  public void setClean() {
    this.isDirty = false;
    this.clientProvidedContent = null;
  }

  public boolean isTest() {
    return Boolean.TRUE == isTest;
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
