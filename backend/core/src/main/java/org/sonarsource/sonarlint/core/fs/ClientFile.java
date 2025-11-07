/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.util.FileUtils;

public class ClientFile {

  private static final String SONARLINT_FOLDER_NAME = ".sonarlint";

  /**
   * Unique identifier for this file on the client side
   */
  private final URI uri;

  private final String configScopeId;

  /**
   * Relative path for this file on the client side. We use the {@link Path} class for convenience for filename separators,
   * but it is not necessary to have a file on the filesystem.
   */
  private final Path relativePath;

  /**
   * For some clients, deciding if a file is a test is costly, and will be computed only when a file is opened in an editor.
   * null means unknown
   */
  @Nullable
  private final Boolean isTest;

  @Nullable
  private final Charset charset;

  /**
   * The absolute path on the local filesystem, if available.
   */
  @Nullable
  private final Path fsPath;
  @Nullable
  private final SonarLanguage detectedLanguage;

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

  private final boolean isUserDefined;

  public ClientFile(URI uri, String configScopeId, Path relativePath, @Nullable Boolean isTest, @Nullable Charset charset, @Nullable Path fsPath,
    @Nullable SonarLanguage detectedLanguage, boolean isUserDefined) {
    this.uri = uri;
    this.configScopeId = configScopeId;
    this.relativePath = relativePath;
    this.isTest = isTest;
    this.charset = charset;
    this.fsPath = fsPath;
    this.detectedLanguage = detectedLanguage;
    this.isUserDefined = isUserDefined;
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
    var charsetToUse = getCharset();
    try {
      return IOUtils.toString(inputStream(), charsetToUse);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read file " + fsPath + "content with charset " + charsetToUse, e);
    }
  }

  public InputStream inputStream() {
    if (isDirty && clientProvidedContent != null) {
      return new ByteArrayInputStream(clientProvidedContent.getBytes(getCharset()));
    }
    if (fsPath == null) {
      throw new IllegalStateException("File " + uri + " is not dirty or does not have content but has no OS Path defined");
    }
    try {
      return BOMInputStream.builder().setInputStream(Files.newInputStream(fsPath))
        // order list of BOMs by length descending, as anyway a sort is made in the constructor
        .setByteOrderMarks(ByteOrderMark.UTF_32LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_8, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE).get();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public Charset getCharset() {
    return charset != null ? charset : Charset.defaultCharset();
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

  public boolean isLargerThan(long size) throws IOException {
    if (isDirty && clientProvidedContent != null) {
      return clientProvidedContent.getBytes(getCharset()).length > size;
    } else {
      var localPath = FileUtils.getFilePathFromUri(uri);
      if (Files.exists(localPath)) {
        return Files.size(localPath) > size;
      }
    }
    return false;
  }

  public boolean isSonarlintConfigurationFile() {
    // Considering .sonarlint/*.json for compatibility with settings exported from Visual Studio
    return isInDotSonarLintFolder() && hasJsonExtension();
  }

  private boolean isInDotSonarLintFolder() {
    var sonarlintPath = getClientRelativePath().getParent();
    return sonarlintPath != null && SONARLINT_FOLDER_NAME.equals(sonarlintPath.getFileName().toString());
  }

  private boolean hasJsonExtension() {
    return "json".equals(FileNameUtils.getExtension(getClientRelativePath()));
  }

  public boolean isTest() {
    return Boolean.TRUE == isTest;
  }

  @Nullable
  public SonarLanguage getDetectedLanguage() {
    return detectedLanguage;
  }

  @Override
  public String toString() {
    return uri.toString();
  }

  public boolean isUserDefined() {
    return isUserDefined;
  }
}
