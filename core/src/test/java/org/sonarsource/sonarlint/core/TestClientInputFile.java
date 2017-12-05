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
package org.sonarsource.sonarlint.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class TestClientInputFile implements ClientInputFile {
  private Path path;
  private boolean isTest;
  private Charset encoding;
  private String language;
  private String relativePath;

  public TestClientInputFile(final Path path, String relativePath, final boolean isTest, final Charset encoding) {
    this(path, relativePath, isTest, encoding, null);
  }

  public TestClientInputFile(final Path path, String relativePath, final boolean isTest, final Charset encoding, @Nullable String language) {
    this.path = path;
    this.relativePath = relativePath;
    this.isTest = isTest;
    this.encoding = encoding;
    this.language = language;
  }

  @Override
  public String getPath() {
    return path.toString();
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
  public String language() {
    return language;
  }

  @Override
  public Charset getCharset() {
    return encoding;
  }

  @Override
  public <G> G getClientObject() {
    return null;
  }

  @Override
  public InputStream inputStream() throws IOException {
    return Files.newInputStream(path);
  }

  @Override
  public String contents() throws IOException {
    return new String(Files.readAllBytes(path), encoding);
  }
}
