/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

class DefaultClientInputFile implements ClientInputFile {

  private final URI fileUri;
  private final String content;
  private final String testFilePattern;

  public DefaultClientInputFile(URI uri, String content, String testFilePattern) {
    this.fileUri = uri;
    this.content = content;
    this.testFilePattern = testFilePattern;
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }

  @Override
  public <G> G getClientObject() {
    return (G) fileUri;
  }

  @Override
  public String getPath() {
    return Paths.get(fileUri).toString();
  }

  @Override
  public boolean isTest() {
    if (testFilePattern == null) {
      return false;
    }
    return Paths.get(fileUri).toFile().getName().matches(testFilePattern);
  }

  @Override
  public String contents() throws IOException {
    return content;
  }

  @Override
  public InputStream inputStream() throws IOException {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }
}
