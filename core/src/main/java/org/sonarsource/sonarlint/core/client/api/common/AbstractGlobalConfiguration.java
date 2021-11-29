/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.analysis.api.ClientFileSystem;
import org.sonarsource.sonarlint.core.plugin.common.Language;
import org.sonarsource.sonarlint.core.plugin.common.log.LogOutput;

/**
 * To use SonarLint in connected mode please provide a server id that will identify the storage.
 * To use in standalone mode please provide list of plugin URLs.
 *
 */
@Immutable
public abstract class AbstractGlobalConfiguration {

  public static final String DEFAULT_WORK_DIR = "work";

  private final LogOutput logOutput;
  private final Path sonarLintUserHome;
  private final Path workDir;
  private final EnumSet<Language> enabledLanguages;
  private final Map<String, String> extraProperties;
  private final Path nodeJsPath;
  private final Version nodeJsVersion;
  private final ClientFileSystem clientFileSystem;
  private final long clientPid;

  protected AbstractGlobalConfiguration(AbstractBuilder<?> builder) {
    this.sonarLintUserHome = builder.sonarlintUserHome != null ? builder.sonarlintUserHome : SonarLintPathManager.home();
    this.workDir = builder.workDir != null ? builder.workDir : this.sonarLintUserHome.resolve(DEFAULT_WORK_DIR);
    this.enabledLanguages = builder.enabledLanguages;
    this.logOutput = builder.logOutput;
    this.extraProperties = new LinkedHashMap<>(builder.extraProperties);
    this.nodeJsPath = builder.nodeJsPath;
    this.nodeJsVersion = builder.nodeJsVersion;
    this.clientFileSystem = builder.clientFileSystem;
    this.clientPid = builder.clientPid;
  }

  public Map<String, String> extraProperties() {
    return Collections.unmodifiableMap(extraProperties);
  }

  public ClientFileSystem getClientFileSystem() {
    return clientFileSystem;
  }

  public Path getSonarLintUserHome() {
    return sonarLintUserHome;
  }

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

  @CheckForNull
  public Version getNodeJsVersion() {
    return nodeJsVersion;
  }

  public long getClientPid() {
    return clientPid;
  }

  public abstract static class AbstractBuilder<G extends AbstractBuilder<G>> {
    private LogOutput logOutput;
    private Path sonarlintUserHome;
    private Path workDir;
    private final EnumSet<Language> enabledLanguages = EnumSet.noneOf(Language.class);
    private Map<String, String> extraProperties = Collections.emptyMap();
    private Path nodeJsPath;
    private Version nodeJsVersion;
    private ClientFileSystem clientFileSystem;
    private long clientPid;

    public G setLogOutput(@Nullable LogOutput logOutput) {
      this.logOutput = logOutput;
      return (G) this;
    }

    /**
     * Override default user home (~/.sonarlint)
     */
    public G setSonarLintUserHome(Path sonarlintUserHome) {
      this.sonarlintUserHome = sonarlintUserHome;
      return (G) this;
    }

    /**
     * Override default work dir (~/.sonarlint/work)
     */
    public G setWorkDir(Path workDir) {
      this.workDir = workDir;
      return (G) this;
    }

    /**
     * Properties that will be passed to global extensions
     */
    public G setExtraProperties(Map<String, String> extraProperties) {
      this.extraProperties = extraProperties;
      return (G) this;
    }

    /**
     * Explicitly enable a {@link Language}
     */
    public G addEnabledLanguage(Language language) {
      enabledLanguages.add(language);
      return (G) this;
    }

    /**
     * Explicitly enable several {@link Language}s
     */
    public G addEnabledLanguages(Language... languages) {
      enabledLanguages.addAll(Arrays.asList(languages));
      return (G) this;
    }

    /**
     * Set the location of the nodejs executable used by some analyzers.
     */
    public G setNodeJs(Path nodeJsPath, Version nodeJsVersion) {
      this.nodeJsPath = nodeJsPath;
      this.nodeJsVersion = nodeJsVersion;
      return (G) this;
    }

    public G setClientFileSystem(ClientFileSystem clientFileSystem) {
      this.clientFileSystem = clientFileSystem;
      return (G) this;
    }

    public G setClientPid(long clientPid) {
      this.clientPid = clientPid;
      return (G) this;
    }
  }

}
