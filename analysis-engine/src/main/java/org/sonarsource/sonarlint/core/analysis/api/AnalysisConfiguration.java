/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

@Immutable
public class AnalysisConfiguration {

  private final Iterable<ClientInputFile> inputFiles;
  private final Map<String, String> extraProperties;
  private final Path baseDir;
  private final Collection<ActiveRule> activeRules;
  private final String toString;

  private AnalysisConfiguration(Builder builder) {
    this.baseDir = builder.baseDir;
    this.inputFiles = builder.inputFiles;
    this.extraProperties = builder.extraProperties;
    this.activeRules = builder.activeRules;
    this.toString = generateToString();
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

  public Iterable<ClientInputFile> inputFiles() {
    return inputFiles;
  }

  public Collection<ActiveRule> activeRules() {
    return activeRules;
  }

  @Override
  public String toString() {
    return toString;
  }

  private String generateToString() {
    var sb = new StringBuilder();
    sb.append("[\n");
    generateToStringCommon(sb);
    sb.append("  activeRules: ").append(activeRules).append("\n");
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
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

  public static final class Builder {
    private final List<ClientInputFile> inputFiles = new ArrayList<>();
    private final Map<String, String> extraProperties = new HashMap<>();
    private Path baseDir;
    private final Collection<ActiveRule> activeRules = new ArrayList<>();

    private Builder() {
    }

    public Builder addInputFiles(ClientInputFile... inputFiles) {
      Collections.addAll(this.inputFiles, inputFiles);
      return this;
    }

    public Builder addInputFiles(Collection<? extends ClientInputFile> inputFiles) {
      this.inputFiles.addAll(inputFiles);
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

    public Builder addActiveRules(ActiveRule... activeRules) {
      Collections.addAll(this.activeRules, activeRules);
      return this;
    }

    public Builder addActiveRules(Collection<? extends ActiveRule> activeRules) {
      this.activeRules.addAll(activeRules);
      return this;
    }

    public Builder addActiveRule(ActiveRule activeRules) {
      this.activeRules.add(activeRules);
      return this;
    }

    public AnalysisConfiguration build() {
      return new AnalysisConfiguration(this);
    }
  }
}
