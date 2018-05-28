/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;

@Immutable
public class StandaloneAnalysisConfiguration {

  private final Iterable<ClientInputFile> inputFiles;
  private final Map<String, String> extraProperties;
  private final Path workDir;
  private final Path baseDir;
  private final Collection<RuleKey> excludedRules;
  private final Collection<RuleKey> includedRules;

  public StandaloneAnalysisConfiguration(Path baseDir, Path workDir, Iterable<ClientInputFile> inputFiles, Map<String, String> extraProperties,
    Collection<RuleKey> excludedRules, Collection<RuleKey> includedRules) {
    this.baseDir = baseDir;
    this.workDir = workDir;
    this.inputFiles = inputFiles;
    this.extraProperties = Collections.unmodifiableMap(new HashMap<>(extraProperties));
    this.excludedRules = Collections.unmodifiableList(new ArrayList<>(excludedRules));
    this.includedRules = Collections.unmodifiableList(new ArrayList<>(includedRules));
  }

  public StandaloneAnalysisConfiguration(Path baseDir, Path workDir, Iterable<ClientInputFile> inputFiles, Map<String, String> extraProperties) {
    this(baseDir, workDir, inputFiles, extraProperties, Collections.emptyList(), Collections.emptyList());
  }

  public Map<String, String> extraProperties() {
    return extraProperties;
  }

  public Path baseDir() {
    return baseDir;
  }

  /**
   * Work dir specific for this analysis.
   */
  public Path workDir() {
    return workDir;
  }

  public Iterable<ClientInputFile> inputFiles() {
    return inputFiles;
  }

  public Collection<RuleKey> excludedRules() {
    return excludedRules;
  }

  public Collection<RuleKey> includedRules() {
    return includedRules;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[\n");
    sb.append("  baseDir: ").append(baseDir).append("\n");
    sb.append("  workDir: ").append(workDir).append("\n");
    sb.append("  extraProperties: ").append(extraProperties).append("\n");
    sb.append("  excludedRules: ").append(excludedRules).append("\n");
    sb.append("  includedRules: ").append(includedRules).append("\n");
    sb.append("  inputFiles: [\n");
    for (ClientInputFile inputFile : inputFiles) {
      sb.append("    ").append(inputFile.getPath());
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
    sb.append("]\n");
    return sb.toString();
  }

  protected String getCharsetLabel(ClientInputFile inputFile) {
    Charset charset = inputFile.getCharset();
    return charset != null ? charset.displayName() : "default";
  }
}
