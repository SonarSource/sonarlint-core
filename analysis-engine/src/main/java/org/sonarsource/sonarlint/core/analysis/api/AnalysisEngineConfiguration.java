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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.commons.Language;

@Immutable
public class AnalysisEngineConfiguration {

  private static final String NODE_EXECUTABLE_PROPERTY = "sonar.nodejs.executable";

  private final Path workDir;
  private final EnumSet<Language> enabledLanguages;
  private final Map<String, String> extraProperties;
  private final Path nodeJsPath;
  private final long clientPid;
  private final ClientModulesProvider modulesProvider;

  private AnalysisEngineConfiguration(Builder builder) {
    this.workDir = builder.workDir;
    this.enabledLanguages = builder.enabledLanguages;
    this.extraProperties = new LinkedHashMap<>(builder.extraProperties);
    this.nodeJsPath = builder.nodeJsPath;
    this.clientPid = builder.clientPid;
    this.modulesProvider = builder.modulesProvider;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Set<Language> getEnabledLanguages() {
    return enabledLanguages;
  }

  public long getClientPid() {
    return clientPid;
  }

  public ClientModulesProvider getModulesProvider() {
    return modulesProvider;
  }

  public Map<String, String> getEffectiveSettings() {
    Map<String, String> props = new HashMap<>(extraProperties);
    if (nodeJsPath != null) {
      props.put(NODE_EXECUTABLE_PROPERTY, nodeJsPath.toString());
    }
    return props;
  }

  public static final class Builder {
    private Path workDir;
    private final EnumSet<Language> enabledLanguages = EnumSet.noneOf(Language.class);
    private Map<String, String> extraProperties = Collections.emptyMap();
    private Path nodeJsPath;
    private long clientPid;
    private ClientModulesProvider modulesProvider;

    private Builder() {

    }

    /**
     * Override default work dir (~/.sonarlint/work)
     */
    public Builder setWorkDir(Path workDir) {
      this.workDir = workDir;
      return this;
    }

    /**
     * Properties that will be passed to global extensions
     */
    public Builder setExtraProperties(Map<String, String> extraProperties) {
      this.extraProperties = extraProperties;
      return this;
    }

    /**
     * Explicitly enable a {@link Language}
     */
    public Builder addEnabledLanguage(Language language) {
      enabledLanguages.add(language);
      return this;
    }

    /**
     * Explicitly enable several {@link Language}s
     */
    public Builder addEnabledLanguages(Language... languages) {
      enabledLanguages.addAll(Arrays.asList(languages));
      return this;
    }

    /**
     * Explicitly enable several {@link Language}s
     */
    public Builder addEnabledLanguages(Collection<Language> languages) {
      enabledLanguages.addAll(languages);
      return this;
    }

    /**
     * Set the location of the nodejs executable used by some analyzers.
     */
    public Builder setNodeJs(@Nullable Path nodeJsPath) {
      this.nodeJsPath = nodeJsPath;
      return this;
    }

    public Builder setClientPid(long clientPid) {
      this.clientPid = clientPid;
      return this;
    }

    public Builder setModulesProvider(ClientModulesProvider modulesProvider) {
      this.modulesProvider = modulesProvider;
      return this;
    }

    public AnalysisEngineConfiguration build() {
      return new AnalysisEngineConfiguration(this);
    }
  }

}
