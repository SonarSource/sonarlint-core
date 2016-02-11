/*
 * SonarLint Core - Implementation
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
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.client.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.IssueListener;
import org.sonarsource.sonarlint.core.client.api.SonarLintClient;
import org.sonarsource.sonarlint.core.client.api.SonarLintException;
import org.sonarsource.sonarlint.core.container.global.GlobalContainer;
import org.sonarsource.sonarlint.core.log.LoggingConfigurator;
import org.sonarsource.sonarlint.core.plugin.LocalPluginIndexProvider;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SonarLintClientImpl implements SonarLintClient {

  private static final Logger LOG = LoggerFactory.getLogger(SonarLintClientImpl.class);

  private final List<Object> globalComponents = Lists.newArrayList();
  private boolean started = false;
  private GlobalContainer globalContainer;

  @Override
  public synchronized void setVerbose(boolean verbose) {
    LoggingConfigurator.setVerbose(verbose);
  }

  @Override
  public synchronized void start(GlobalConfiguration globalConfig) {
    if (started) {
      throw new IllegalStateException("SonarLint Engine is already started");
    }

    globalComponents.add(globalConfig);
    globalComponents.add(new LocalPluginIndexProvider(globalConfig.getPluginUrls()));
    LoggingConfigurator.init(globalConfig.isVerbose(), globalConfig.getLogOutput());
    this.globalContainer = GlobalContainer.create(globalComponents);
    try {
      globalContainer.startComponents();
    } catch (RuntimeException e) {
      throw handleException(e);
    }
    this.started = true;
  }

  @Override
  public String getHtmlRuleDescription(String ruleKey) {
    checkStarted();
    return globalContainer.getHtmlRuleDescription(ruleKey);
  }

  @Override
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
      throw new IllegalStateException("SonarLint Engine is not started");
    }
  }

  private static RuntimeException handleException(RuntimeException t) {
    if (LOG.isDebugEnabled()) {
      // In DEBUG mode always return full exception
      return convertToSonarLintException(t);
    }

    for (Throwable y : Throwables.getCausalChain(t)) {
      if (y instanceof MessageException) {
        return convertToSonarLintException(y);
      }
    }

    return convertToSonarLintException(t);
  }

  private static RuntimeException convertToSonarLintException(@Nullable Throwable t) {
    if (t == null) {
      return null;
    }
    Throwable cause = convertToSonarLintException(t.getCause());
    SonarLintException sonarLintException = new SonarLintException(t.toString(), t.getMessage(), cause);
    sonarLintException.setStackTrace(t.getStackTrace());
    return sonarLintException;
  }

  @Override
  public synchronized void stop() {
    checkStarted();
    try {
      globalContainer.stopComponents(false);
    } catch (RuntimeException e) {
      throw handleException(e);
    } finally {
      this.globalContainer = null;
      this.globalComponents.clear();
    }
    this.started = false;
  }

}
