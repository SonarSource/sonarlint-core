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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class AnalysisSchedulerConfiguration {

  private static final String NODE_EXECUTABLE_PROPERTY = "sonar.nodejs.executable";

  private final Path workDir;
  private final Map<String, String> extraProperties;
  private final Path nodeJsPath;
  private final long clientPid;
  private final Function<String, ClientModuleFileSystem> fileSystemProvider;

  private AnalysisSchedulerConfiguration(Builder builder) {
    this.workDir = builder.workDir;
    this.extraProperties = new LinkedHashMap<>(builder.extraProperties);
    this.nodeJsPath = builder.nodeJsPath;
    this.clientPid = builder.clientPid;
    this.fileSystemProvider = builder.fileSystemProvider;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Path getWorkDir() {
    return workDir;
  }

  public long getClientPid() {
    return clientPid;
  }

  public Function<String, ClientModuleFileSystem> getFileSystemProvider() {
    return fileSystemProvider;
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
    private Map<String, String> extraProperties = Collections.emptyMap();
    private Path nodeJsPath;
    private long clientPid;
    private Function<String, ClientModuleFileSystem> fileSystemProvider = key -> null;

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

    public Builder setFileSystemProvider(Function<String, ClientModuleFileSystem> fileSystemProvider) {
      this.fileSystemProvider = fileSystemProvider;
      return this;
    }

    public AnalysisSchedulerConfiguration build() {
      return new AnalysisSchedulerConfiguration(this);
    }
  }

}
