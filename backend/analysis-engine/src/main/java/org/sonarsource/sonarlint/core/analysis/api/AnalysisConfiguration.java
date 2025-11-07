/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.batch.rule.ActiveRule;

@Immutable
public class AnalysisConfiguration {

  private final List<ClientInputFile> inputFiles;
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

  public List<ClientInputFile> inputFiles() {
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
    generateToStringActiveRules(sb);
    generateToStringInputFiles(sb);
    sb.append("]\n");
    return sb.toString();
  }

  protected void generateToStringActiveRules(StringBuilder sb) {
    if ("true".equals(System.getProperty("sonarlint.debug.active.rules"))) {
      sb.append("  activeRules: ").append(activeRules).append("\n");
    } else {
      // Group active rules by language and count occurrences
      var languageCounts = new HashMap<String, Integer>();
      for (var rule : activeRules) {
        var languageKey = rule.ruleKey().toString().split(":")[0];
        languageCounts.put(languageKey, languageCounts.getOrDefault(languageKey, 0) + 1);
      }

      sb.append("  activeRules: [");
      languageCounts.forEach((language, count) -> sb.append(count).append(" ").append(language).append(", "));
      if (!languageCounts.isEmpty()) {
        // Remove the trailing comma and space
        sb.setLength(sb.length() - 2);
      }
      sb.append("]\n");
    }
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
        sb.append(" [" + language.getSonarLanguageKey() + "]");
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
      extraProperties.forEach(this::putExtraProperty);
      return this;
    }

    public Builder putExtraProperty(String key, String value) {
      this.extraProperties.put(key.trim(), value);
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
