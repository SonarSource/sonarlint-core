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
package org.sonarsource.sonarlint.core.analysis;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import org.apache.commons.io.IOUtils;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.util.FileUtils;
import org.sonarsource.sonarlint.core.fs.ClientFile;

public class BackendInputFile implements ClientInputFile {
  private final ClientFile clientFile;

  public BackendInputFile(ClientFile clientFile) {
    this.clientFile = clientFile;
  }

  @Override
  public String getPath() {
    return FileUtils.getFilePathFromUri(clientFile.getUri()).toAbsolutePath().toString();
  }

  @Override
  public boolean isTest() {
    return clientFile.isTest();
  }

  @Override
  public Charset getCharset() {
    return clientFile.getCharset();
  }

  @Override
  public ClientFile getClientObject() {
    return clientFile;
  }

  @Override
  public InputStream inputStream() {
    return IOUtils.toInputStream(contents(), getCharset());
  }

  @Override
  public String contents() {
    return clientFile.getContent();
  }

  @Override
  public String relativePath() {
    return clientFile.getClientRelativePath().toString();
  }

  @Override
  public URI uri() {
    return clientFile.getUri();
  }

  @Override
  public SonarLanguage language() {
    return clientFile.getDetectedLanguage();
  }

  @Override
  public boolean isDirty() {
    return clientFile.isDirty();
  }
}
