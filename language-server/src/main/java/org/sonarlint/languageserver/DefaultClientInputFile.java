/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class DefaultClientInputFile implements ClientInputFile {

  private final URI fileUri;
  private final String content;
  private final String sqLanguage;
  private final String relativePath;
  private final boolean isTest;

  public DefaultClientInputFile(URI uri, String relativePath, String content, boolean isTest, @Nullable String clientLanguageId) {
    this.relativePath = relativePath;
    this.fileUri = uri;
    this.content = content;
    this.isTest = isTest;
    this.sqLanguage = toSqLanguage(clientLanguageId);
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }

  @Override
  public URI getClientObject() {
    return fileUri;
  }

  @Override
  public String getPath() {
    return Paths.get(fileUri).toString();
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
  public String contents() throws IOException {
    return content;
  }

  @Override
  public InputStream inputStream() throws IOException {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String language() {
    return this.sqLanguage;
  }

  @CheckForNull
  private static String toSqLanguage(@Nullable String clientLanguageId) {
    if (clientLanguageId == null) {
      return null;
    }
    // See https://microsoft.github.io/language-server-protocol/specification#textdocumentitem
    switch (clientLanguageId) {
      case "javascript":
      case "javascriptreact":
      case "vue":
      case "vue component":
      case "babel es6 javascript":
        return "js";
      case "python":
        return "py";
      case "typescript":
      case "typescriptreact":
        return "ts";
      default:
        return clientLanguageId;
    }
  }
}
