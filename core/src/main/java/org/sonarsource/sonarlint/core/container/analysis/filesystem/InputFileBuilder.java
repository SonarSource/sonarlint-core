/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.nio.charset.Charset;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

public class InputFileBuilder {

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
    String lang = langDetection.language(defaultInputFile);
    defaultInputFile.setLanguage(lang);

    Charset charset = inputFile.getCharset();
    defaultInputFile.init(fileMetadata.readMetadata(inputFile.getPath().toFile(), charset != null ? charset : Charset.defaultCharset()));
    return defaultInputFile;
  }

}
