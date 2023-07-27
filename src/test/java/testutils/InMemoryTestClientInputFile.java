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
package testutils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonar.api.utils.PathUtils;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.Language;

public class InMemoryTestClientInputFile implements ClientInputFile {
  private boolean isTest;
  private Language language;
  private String relativePath;
  private String contents;
  private Path path;

  public InMemoryTestClientInputFile(String contents, String relativePath, @Nullable Path path, final boolean isTest, @Nullable Language language) {
    this.contents = contents;
    this.relativePath = relativePath;
    this.path = path;
    this.isTest = isTest;
    this.language = language;
  }

  @Override
  public String getPath() {
    if (path == null) {
      throw new UnsupportedOperationException("getPath");
    }
    return PathUtils.sanitize(path.toString());
  }

  @Override
  public String relativePath() {
    return relativePath;
  }

  @Override
  public boolean isTest() {
    return isTest;
  }

  @Override
  public Language language() {
    return language;
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }

  @Override
  public <G> G getClientObject() {
    return null;
  }

  @Override
  public InputStream inputStream() throws IOException {
    return new ByteArrayInputStream(relativePath.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String contents() throws IOException {
    return contents;
  }

  @Override
  public URI uri() {
    if (path == null) {
      return URI.create("file://" + relativePath);
    }
    return path.toUri();
  }
}
