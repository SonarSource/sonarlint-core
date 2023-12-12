/*
 * SonarLint Core - Java Client Legacy
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
package org.sonarsource.sonarlint.core.client.legacy.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;

@Immutable
public class AnalysisConfiguration {

  private final List<ClientInputFile> inputFiles;
  private final Map<String, String> extraProperties;
  private final Path baseDir;
  private final Object moduleKey;

  protected AnalysisConfiguration(Builder builder) {
    this.baseDir = builder.baseDir;
    this.inputFiles = builder.inputFiles;
    this.extraProperties = builder.extraProperties;
    this.moduleKey = builder.moduleKey;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, String> extraProperties() {
    return extraProperties;
  }

  public Path baseDir() {
    return baseDir;
  }

  @CheckForNull
  public Object moduleKey() {
    return moduleKey;
  }

  public List<ClientInputFile> inputFiles() {
    return inputFiles;
  }

  @Override
  public String toString() {
    var sb = new StringBuilder();
    sb.append("[\n");
    generateToStringCommon(sb);
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
  }

  private void generateToStringCommon(StringBuilder sb) {
    sb.append("  baseDir: ").append(baseDir()).append("\n");
    sb.append("  extraProperties: ").append(extraProperties()).append("\n");
    sb.append("  moduleKey: ").append(moduleKey()).append("\n");
  }

  private void generateToStringInputFiles(StringBuilder sb) {
    sb.append("  inputFiles: [\n");
    for (ClientInputFile inputFile : inputFiles()) {
      sb.append("    ").append(inputFile.uri());
      sb.append(" (").append(getCharsetLabel(inputFile)).append(")");
      if (inputFile.isTest()) {
        sb.append(" [test]");
      }
      var language = inputFile.language();
      if (language != null) {
        sb.append(" [" + language.getLanguageKey() + "]");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
  }

  private static String getCharsetLabel(ClientInputFile inputFile) {
    var charset = inputFile.getCharset();
    return charset != null ? charset.displayName() : "default";
  }

  public static class Builder {
    private final List<ClientInputFile> inputFiles = new ArrayList<>();
    private final Map<String, String> extraProperties = new HashMap<>();
    private Path baseDir;
    private Object moduleKey;

    public Builder addInputFiles(ClientInputFile... inputFiles) {
      Collections.addAll(this.inputFiles, inputFiles);
      return this;
    }

    public Builder addInputFile(ClientInputFile inputFile) {
      this.inputFiles.add(inputFile);
      return this;
    }

    public Builder putAllExtraProperties(Map<String, String> extraProperties) {
      this.extraProperties.putAll(extraProperties);
      return this;
    }

    public Builder putExtraProperty(String key, String value) {
      this.extraProperties.put(key, value);
      return this;
    }

    public Builder setBaseDir(Path baseDir) {
      this.baseDir = baseDir;
      return this;
    }

    public Builder setModuleKey(@Nullable Object moduleKey) {
      this.moduleKey = moduleKey;
      return this;
    }

    public AnalysisConfiguration build() {
      return new AnalysisConfiguration(this);
    }

  }
}
