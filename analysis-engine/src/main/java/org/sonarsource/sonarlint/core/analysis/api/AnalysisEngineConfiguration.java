/*
 * SonarLint Core - Analysis Engine
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.plugin.common.Version;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;

@Immutable
public class AnalysisEngineConfiguration {

  private static final String NODE_EXECUTABLE_PROPERTY = "sonar.nodejs.executable";

  private final Set<Path> pluginsJarPaths;
  private final LogOutput logOutput;
  private final Path workDir;
  private final EnumSet<Language> enabledLanguages;
  private final Map<String, String> extraProperties;
  private final Path nodeJsPath;
  private final Version nodeJsVersion;
  private final long clientPid;
  private final ClientFileSystem clientFileSystem;

  private AnalysisEngineConfiguration(Builder builder) {
    this.workDir = builder.workDir;
    this.enabledLanguages = builder.enabledLanguages;
    this.logOutput = builder.logOutput;
    this.extraProperties = new LinkedHashMap<>(builder.extraProperties);
    this.nodeJsPath = builder.nodeJsPath;
    this.nodeJsVersion = builder.nodeJsVersion;
    this.clientPid = builder.clientPid;
    this.pluginsJarPaths = Set.copyOf(builder.pluginsJarPaths);
    this.clientFileSystem = builder.clientFileSystem;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, String> extraProperties() {
    return Collections.unmodifiableMap(extraProperties);
  }

  @CheckForNull
  public Path getWorkDir() {
    return workDir;
  }

  public Set<Language> getEnabledLanguages() {
    return enabledLanguages;
  }

  @CheckForNull
  public LogOutput getLogOutput() {
    return logOutput;
  }

  @CheckForNull
  public Path getNodeJsPath() {
    return nodeJsPath;
  }

  public Optional<Version> getNodeJsVersion() {
    return Optional.ofNullable(nodeJsVersion);
  }

  public long getClientPid() {
    return clientPid;
  }

  public Set<Path> getPluginsJarPaths() {
    return pluginsJarPaths;
  }

  public ClientFileSystem getClientFileSystem() {
    return clientFileSystem;
  }

  public Map<String, String> getEffectiveConfig() {
    Map<String, String> props = new HashMap<>();
    if (nodeJsPath != null) {
      props.put(NODE_EXECUTABLE_PROPERTY, nodeJsPath.toString());
    }
    props.putAll(extraProperties);
    return props;
  }

  public static final class Builder {
    private LogOutput logOutput;
    private Path workDir;
    private final EnumSet<Language> enabledLanguages = EnumSet.noneOf(Language.class);
    private Map<String, String> extraProperties = Collections.emptyMap();
    private Path nodeJsPath;
    private Version nodeJsVersion;
    private long clientPid;
    private final Set<Path> pluginsJarPaths = new LinkedHashSet<>();
    private ClientFileSystem clientFileSystem;

    private Builder() {

    }

    public Builder setLogOutput(@Nullable LogOutput logOutput) {
      this.logOutput = logOutput;
      return this;
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
    public Builder setNodeJs(@Nullable Path nodeJsPath, @Nullable Version nodeJsVersion) {
      this.nodeJsPath = nodeJsPath;
      this.nodeJsVersion = nodeJsVersion;
      return this;
    }

    public Builder setClientPid(long clientPid) {
      this.clientPid = clientPid;
      return this;
    }

    public Builder addPlugins(Path... pluginsJarPaths) {
      Collections.addAll(this.pluginsJarPaths, pluginsJarPaths);
      return this;
    }

    public Builder addPlugins(Collection<Path> pluginsJarPaths) {
      this.pluginsJarPaths.addAll(pluginsJarPaths);
      return this;
    }

    public Builder addPlugin(Path pluginJarPath) {
      this.pluginsJarPaths.add(pluginJarPath);
      return this;
    }

    public Builder setClientFileSystem(ClientFileSystem clientFileSystem) {
      this.clientFileSystem = clientFileSystem;
      return this;
    }

    public AnalysisEngineConfiguration build() {
      return new AnalysisEngineConfiguration(this);
    }
  }

}
