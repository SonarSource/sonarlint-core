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

import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.SonarLintClient;
import org.sonarsource.sonarlint.core.client.api.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleSyncStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.global.GlobalContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageGlobalContainer;
import org.sonarsource.sonarlint.core.log.LoggingConfigurator;

import static com.google.common.base.Preconditions.checkNotNull;

public final class SonarLintClientImpl implements SonarLintClient {

  private static final Logger LOG = LoggerFactory.getLogger(SonarLintClientImpl.class);

  private volatile boolean started = false;
  private final GlobalConfiguration globalConfig;
  private GlobalContainer globalContainer;
  private final ReadWriteLock rwl = new ReentrantReadWriteLock();

  public SonarLintClientImpl(GlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
    LoggingConfigurator.init(globalConfig.isVerbose(), globalConfig.getLogOutput());
  }

  @Override
  public void setVerbose(boolean verbose) {
    rwl.writeLock().lock();
    try {
      LoggingConfigurator.setVerbose(verbose);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  public GlobalContainer getGlobalContainer() {
    rwl.readLock().lock();
    try {
      checkStarted();
      return globalContainer;
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public synchronized void start() {
    rwl.writeLock().lock();
    try {
      if (started) {
        throw new IllegalStateException("SonarLint Engine is already started");
      }
      this.globalContainer = GlobalContainer.create(globalConfig);
      try {
        globalContainer.startComponents();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
      this.started = true;
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    rwl.readLock().lock();
    try {
      checkStarted();
      return globalContainer.getRuleDetails(ruleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public AnalysisResults analyze(AnalysisConfiguration configuration, IssueListener issueListener) {
    checkNotNull(configuration);
    checkNotNull(issueListener);
    rwl.readLock().lock();
    try {
      checkStarted();
      try {
        return globalContainer.analyze(configuration, issueListener);
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalSyncStatus getSyncStatus() {
    checkMode();
    rwl.readLock().lock();
    try {
      checkStarted();
      return ((StorageGlobalContainer) globalContainer).getSyncStatus();
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void sync(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    checkMode();
    rwl.writeLock().lock();
    try {
      checkStopped();
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
      try {
        connectedContainer.sync();
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.build(e);
        }
      }
    } finally {
      rwl.writeLock().unlock();
    }
  }

  private void checkMode() {
    if (globalConfig.getServerId() == null) {
      throw new UnsupportedOperationException("Unable to sync in unconnected mode");
    }
  }

  @Override
  public ValidationResult validateCredentials(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    checkMode();
    rwl.readLock().lock();
    try {
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
      try {
        return connectedContainer.validateCredentials();
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.build(e);
        }
      }
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public List<RemoteModule> searchModule(ServerConfiguration serverConfig, String exactKeyOrPartialName) {
    checkNotNull(serverConfig);
    checkMode();
    rwl.readLock().lock();
    try {
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
      try {
        return connectedContainer.searchModule(exactKeyOrPartialName);
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.build(e);
        }
      }
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void syncModule(ServerConfiguration serverConfig, String moduleKey) {
    checkNotNull(serverConfig);
    checkMode();
    rwl.writeLock().lock();
    try {
      checkStopped();
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      }
      try {
        connectedContainer.syncModule(moduleKey);
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.build(e);
        }
      }
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ModuleSyncStatus getModuleSyncStatus(String moduleKey) {
    checkMode();
    rwl.readLock().lock();
    try {
      checkStarted();
      return ((StorageGlobalContainer) globalContainer).getModuleSyncStatus(moduleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  private void checkStarted() {
    if (!started) {
      throw new IllegalStateException("SonarLint Engine is not started");
    }
  }

  private void checkStopped() {
    if (started) {
      throw new IllegalStateException("SonarLint Engine should be stopped");
    }
  }

  @Override
  public void stop() {
    rwl.writeLock().lock();
    try {
      checkStarted();
      try {
        globalContainer.stopComponents(false);
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      } finally {
        this.globalContainer = null;
      }
      this.started = false;
    } finally {
      rwl.writeLock().unlock();
    }
  }

}
