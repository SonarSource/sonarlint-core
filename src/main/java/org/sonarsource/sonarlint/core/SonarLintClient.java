/*
 * SonarLint Core Library
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
package org.sonarsource.sonarlint.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.picocontainer.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.container.global.GlobalContainer;
import org.sonarsource.sonarlint.core.log.LoggingConfigurator;
import org.sonarsource.sonarlint.core.plugin.LocalPluginIndexProvider;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Entry point for SonarLint.
 */
public final class SonarLintClient {

  private static final Logger LOG = LoggerFactory.getLogger(SonarLintClient.class);

  private boolean started = false;
  private final GlobalContainer globalContainer;

  private SonarLintClient(Builder builder) {
    List<Object> components = Lists.newArrayList();
    components.add(new GlobalConfiguration(builder.sonarUserHome, builder.workDir));
    components.add(new LocalPluginIndexProvider(builder.pluginUrls));
    components.addAll(builder.components);
    LoggingConfigurator.init(builder.verbose, builder.logOutput);
    this.globalContainer = GlobalContainer.create(components);
  }

  /**
   * Change verbosity at runtime
   */
  public synchronized void setVerbose(boolean verbose) {
    LoggingConfigurator.setVerbose(verbose);
  }

  public synchronized SonarLintClient start() {
    if (started) {
      throw new IllegalStateException("SonarLint Engine is already started");
    }

    try {
      globalContainer.startComponents();
    } catch (RuntimeException e) {
      throw handleException(e);
    }
    this.started = true;

    return this;
  }

  /**
   * Return rule description.
   * @param ruleKey See {@link IssueListener.Issue#getRuleKey()}
   * @return Html rule description
   * @throws IllegalArgumentException if ruleKey is unknown
   * @since 1.0
   */
  public String getHtmlRuleDescription(String ruleKey) {
    checkStarted();
    return globalContainer.getHtmlRuleDescription(ruleKey);
  }

  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener) {
    checkNotNull(configuration);
    Preconditions.checkNotNull(issueListener);
    checkStarted();
    try {
      return globalContainer.analyze(configuration, issueListener);
    } catch (RuntimeException e) {
      throw handleException(e);
    }
  }

  private void checkStarted() {
    if (!started) {
      throw new IllegalStateException("SonarLint client is not started.");
    }
  }

  private RuntimeException handleException(RuntimeException t) {
    if (LOG.isDebugEnabled()) {
      return t;
    }

    for (Throwable y : Throwables.getCausalChain(t)) {
      if (y instanceof MessageException) {
        return (MessageException) y;
      }
    }

    return t;
  }

  public synchronized void stop() {
    doStop(false);
  }

  private void doStop(boolean swallowException) {
    checkStarted();
    try {
      globalContainer.stopComponents(swallowException);
    } catch (RuntimeException e) {
      throw handleException(e);
    }
    this.started = false;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private List<Object> components = Lists.newArrayList();
    private boolean verbose = false;
    private LogOutput logOutput;
    private Path sonarUserHome;
    private Path workDir;
    private List<URL> pluginUrls = Lists.newArrayList();

    private Builder() {
    }

    public Builder setLogOutput(@Nullable LogOutput logOutput) {
      this.logOutput = logOutput;
      return this;
    }

    public Builder addComponents(Object... components) {
      Collections.addAll(this.components, components);
      return this;
    }

    public Builder addComponent(Object component) {
      this.components.add(component);
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
      this.sonarUserHome = sonarUserHome;
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
      Collections.addAll(this.pluginUrls, pluginUrls);
      return this;
    }

    public Builder addPlugin(URL pluginUrl) {
      this.pluginUrls.add(pluginUrl);
      return this;
    }

    public SonarLintClient build() {
      return new SonarLintClient(this);
    }
  }
}
