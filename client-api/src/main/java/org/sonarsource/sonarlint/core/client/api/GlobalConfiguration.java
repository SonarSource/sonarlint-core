/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.client.api;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * To use SonarLint in connected mode please provide a server id that will identify the storage.
 * To use in standalone mode please provide list of plugin URLs.  
 *
 */
@Immutable
public class GlobalConfiguration {

  public static final String DEFAULT_WORK_DIR = "work";
  public static final String DEFAULT_STORAGE_DIR = "storage";

  private final boolean verbose;
  private final LogOutput logOutput;
  private final List<URL> pluginUrls;
  private final Path sonarLintUserHome;
  private final Path workDir;
  private final String serverId;
  private final Path storageRoot;

  private GlobalConfiguration(Builder builder) {
    this.sonarLintUserHome = builder.sonarlintUserHome != null ? builder.sonarlintUserHome : findHome();
    this.workDir = builder.workDir != null ? builder.workDir : this.sonarLintUserHome.resolve(DEFAULT_WORK_DIR);
    this.verbose = builder.verbose;
    this.logOutput = builder.logOutput;
    this.pluginUrls = builder.pluginUrls;
    this.serverId = builder.serverId;
    this.storageRoot = builder.storageRoot != null ? builder.storageRoot : this.sonarLintUserHome.resolve(DEFAULT_STORAGE_DIR);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Path getSonarLintUserHome() {
    return sonarLintUserHome;
  }

  public Path getWorkDir() {
    return workDir;
  }

  public Path getStorageRoot() {
    return storageRoot;
  }

  public boolean isVerbose() {
    return verbose;
  }

  @CheckForNull
  public LogOutput getLogOutput() {
    return logOutput;
  }

  @CheckForNull
  public String getServerId() {
    return serverId;
  }

  public List<URL> getPluginUrls() {
    return Collections.unmodifiableList(pluginUrls);
  }

  private static Path findHome() {
    String path = System.getenv("SONARLINT_USER_HOME");
    if (path == null) {
      // Default
      path = System.getProperty("user.home") + File.separator + ".sonarlint";
    }
    return Paths.get(path);
  }

  public static final class Builder {
    private boolean verbose = false;
    private LogOutput logOutput;
    private Path sonarlintUserHome;
    private Path workDir;
    private List<URL> pluginUrls = new ArrayList<>();
    private String serverId;
    private Path storageRoot;

    private Builder() {
    }

    public Builder setLogOutput(@Nullable LogOutput logOutput) {
      this.logOutput = logOutput;
      return this;
    }

    public Builder setVerbose(boolean verbose) {
      this.verbose = verbose;
      return this;
    }

    /**
     * Override default user home (~/.sonarlint)
     */
    public Builder setSonarLintUserHome(Path sonarUserHome) {
      this.sonarlintUserHome = sonarUserHome;
      return this;
    }

    /**
     * Override default work dir (~/.sonarlint/work)
     */
    public Builder setWorkDir(Path workDir) {
      this.workDir = workDir;
      return this;
    }

    public Builder addPlugins(URL... pluginUrls) {
      checkNoServer();

      Collections.addAll(this.pluginUrls, pluginUrls);
      return this;
    }

    public Builder addPlugin(URL pluginUrl) {
      checkNoServer();
      this.pluginUrls.add(pluginUrl);
      return this;
    }

    private void checkNoServer() {
      if (serverId != null) {
        throw new UnsupportedOperationException("Server id already configured");
      }
    }

    public Builder setServerId(String serverId) {
      if (!pluginUrls.isEmpty()) {
        throw new UnsupportedOperationException("Manual plugins already configured");
      }
      this.serverId = serverId;
      return this;
    }

    /**
     * Override default storage dir (~/.sonarlint/storage)
     */
    public Builder setStorageRoot(Path storageRoot) {
      this.storageRoot = storageRoot;
      return this;
    }

    public GlobalConfiguration build() {
      return new GlobalConfiguration(this);
    }
  }

}
