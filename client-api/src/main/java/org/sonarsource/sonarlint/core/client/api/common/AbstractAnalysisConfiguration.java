/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

@Immutable
public abstract class AbstractAnalysisConfiguration {

  private final Iterable<ClientInputFile> inputFiles;
  private final Map<String, String> extraProperties;
  private final Path baseDir;

  protected AbstractAnalysisConfiguration(AbstractBuilder<?> builder) {
    this.baseDir = builder.baseDir;
    this.inputFiles = builder.inputFiles;
    this.extraProperties = builder.extraProperties;
  }

  public Map<String, String> extraProperties() {
    return extraProperties;
  }

  public Path baseDir() {
    return baseDir;
  }

  public Iterable<ClientInputFile> inputFiles() {
    return inputFiles;
  }

  protected void generateToStringCommon(StringBuilder sb) {
    sb.append("  baseDir: ").append(baseDir()).append("\n");
    sb.append("  extraProperties: ").append(extraProperties()).append("\n");
  }

  protected void generateToStringInputFiles(StringBuilder sb) {
    sb.append("  inputFiles: [\n");
    for (ClientInputFile inputFile : inputFiles()) {
      sb.append("    ").append(inputFile.uri());
      sb.append(" (").append(getCharsetLabel(inputFile)).append(")");
      if (inputFile.isTest()) {
        sb.append(" [test]");
      }
      if (inputFile.language() != null) {
        sb.append(" [" + inputFile.language() + "]");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
  }

  private static String getCharsetLabel(ClientInputFile inputFile) {
    Charset charset = inputFile.getCharset();
    return charset != null ? charset.displayName() : "default";
  }

  public abstract static class AbstractBuilder<G extends AbstractBuilder<G>> {
    private List<ClientInputFile> inputFiles = new ArrayList<>();
    private Map<String, String> extraProperties = new HashMap<>();
    private Path baseDir;

    public G addInputFiles(ClientInputFile... inputFiles) {
      Collections.addAll(this.inputFiles, inputFiles);
      return (G) this;
    }

    public G addInputFiles(Collection<? extends ClientInputFile> inputFiles) {
      this.inputFiles.addAll(inputFiles);
      return (G) this;
    }

    public G addInputFile(ClientInputFile inputFile) {
      this.inputFiles.add(inputFile);
      return (G) this;
    }

    public G putAllExtraProperties(Map<String, String> extraProperties) {
      this.extraProperties.putAll(extraProperties);
      return (G) this;
    }

    public G putExtraProperty(String key, String value) {
      this.extraProperties.put(key, value);
      return (G) this;
    }

    public G setBaseDir(Path baseDir) {
      this.baseDir = baseDir;
      return (G) this;
    }

  }
}
