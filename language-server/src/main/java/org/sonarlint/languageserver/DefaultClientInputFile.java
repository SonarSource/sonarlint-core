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
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

class DefaultClientInputFile implements ClientInputFile {

  private final URI fileUri;
  private final String content;
  private final PathMatcher testMatcher;
  private final String sqLanguage;

  public DefaultClientInputFile(URI uri, String content, @Nullable String testFilePattern, @Nullable String clientLanguageId) {
    this.fileUri = uri;
    this.content = content;
    this.sqLanguage = toSqLanguage(clientLanguageId);
    testMatcher = testFilePattern != null ? FileSystems.getDefault().getPathMatcher("glob:" + testFilePattern) : null;

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
    return testMatcher != null && testMatcher.matches(Paths.get(fileUri));
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
    switch (clientLanguageId) {
      case "javascript":
      case "javascriptreact":
      case "vue component":
        return "js";
      case "python":
        return "py";
      default:
        return clientLanguageId;
    }
  }
}
