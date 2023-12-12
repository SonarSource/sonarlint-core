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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.SonarLintUserHome;

public class EngineConfiguration {

  public static final String DEFAULT_WORK_DIR = "work";

  private final ClientLogOutput logOutput;
  private final Path workDir;
  private final Map<String, String> extraProperties;
  private final ClientModulesProvider modulesProvider;
  private final long clientPid;

  protected EngineConfiguration(Builder builder) {
    var sonarLintUserHome = builder.sonarlintUserHome != null ? builder.sonarlintUserHome : SonarLintUserHome.get();
    this.workDir = builder.workDir != null ? builder.workDir : sonarLintUserHome.resolve(DEFAULT_WORK_DIR);
    this.logOutput = builder.logOutput;
    this.extraProperties = new LinkedHashMap<>(builder.extraProperties);
    this.modulesProvider = builder.modulesProvider;
    this.clientPid = builder.clientPid;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, String> extraProperties() {
    return Collections.unmodifiableMap(extraProperties);
  }

  public ClientModulesProvider getModulesProvider() {
    return modulesProvider;
  }

  public Path getWorkDir() {
    return workDir;
  }

  @CheckForNull
  public ClientLogOutput getLogOutput() {
    return logOutput;
  }

  public long getClientPid() {
    return clientPid;
  }

  public static class Builder {
    private ClientLogOutput logOutput;
    private Path sonarlintUserHome;
    private Path workDir;
    private Map<String, String> extraProperties = Collections.emptyMap();
    private ClientModulesProvider modulesProvider;
    private long clientPid;

    public Builder setLogOutput(@Nullable ClientLogOutput logOutput) {
      this.logOutput = logOutput;
      return this;
    }

    /**
     * Override default user home (~/.sonarlint)
     */
    public Builder setSonarLintUserHome(Path sonarlintUserHome) {
      this.sonarlintUserHome = sonarlintUserHome;
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

    public Builder setModulesProvider(ClientModulesProvider modulesProvider) {
      this.modulesProvider = modulesProvider;
      return this;
    }

    public Builder setClientPid(long clientPid) {
      this.clientPid = clientPid;
      return this;
    }

    public EngineConfiguration build() {
      return new EngineConfiguration(this);
    }
  }

}
