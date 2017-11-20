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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import javax.annotation.CheckForNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class InputFileBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(InputFileBuilder.class);
  private final LanguageDetection langDetection;
  private final FileMetadata fileMetadata;

  public InputFileBuilder(LanguageDetection langDetection, FileMetadata fileMetadata) {
    this.langDetection = langDetection;
    this.fileMetadata = fileMetadata;
  }

  LanguageDetection langDetection() {
    return langDetection;
  }

  @CheckForNull
  SonarLintInputFile create(ClientInputFile inputFile) {
    SonarLintInputFile defaultInputFile = new SonarLintInputFile(inputFile);
    defaultInputFile.setType(inputFile.isTest() ? Type.TEST : Type.MAIN);
    if (inputFile.language() != null) {
      LOG.debug("Language of file '{}' is set to '{}'", inputFile.uri(), inputFile.language());
      defaultInputFile.setLanguage(inputFile.language());
    } else {
      defaultInputFile.setLanguage(langDetection.language(defaultInputFile));
    }

    Charset charset = inputFile.getCharset();
    InputStream stream;
    try {
      stream = inputFile.inputStream();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to open a stream on file: " + inputFile.getPath(), e);
    }
    defaultInputFile.init(fileMetadata.readMetadata(stream, charset != null ? charset : Charset.defaultCharset(), inputFile.getPath()));
    return defaultInputFile;
  }

}
