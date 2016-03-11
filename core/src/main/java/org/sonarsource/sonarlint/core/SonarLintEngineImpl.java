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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.StateListener;
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

public final class SonarLintEngineImpl implements SonarLintEngine {

  private final GlobalConfiguration globalConfig;
  private GlobalContainer globalContainer;
  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final List<StateListener> listeners = new ArrayList<>();
  private State state = State.UNKNOW;

  public SonarLintEngineImpl(GlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
    LoggingConfigurator.init(globalConfig.isVerbose(), globalConfig.getLogOutput());
    start();
  }

  @Override
  public State getState() {
    return state;
  }

  @Override
  public void addStateListener(StateListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeStateListener(StateListener listener) {
    listeners.remove(listener);
  }

  private void changeState(State state) {
    this.state = state;
    for (StateListener listener : listeners) {
      listener.stateChanged(state);
    }
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
    return globalContainer;
  }

  public void start() {
    rwl.writeLock().lock();
    this.globalContainer = GlobalContainer.create(globalConfig);
    try {
      globalContainer.startComponents();
      if (globalContainer instanceof StorageGlobalContainer) {
        changeState(((StorageGlobalContainer) globalContainer).getSyncStatus() != null ? State.SYNCED : State.NOT_SYNCED);
      }
    } catch (RuntimeException e) {
      changeState(State.UNKNOW);
      throw SonarLintWrappedException.build(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    rwl.readLock().lock();
    try {
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
      return globalContainer.analyze(configuration, issueListener);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.build(e);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalSyncStatus getSyncStatus() {
    checkConnectedMode();
    rwl.readLock().lock();
    try {
      return ((StorageGlobalContainer) globalContainer).getSyncStatus();
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalSyncStatus sync(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    checkConnectedMode();
    rwl.writeLock().lock();
    stop();
    changeState(State.SYNCING);
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      try {
        connectedContainer.startComponents();
        connectedContainer.sync();
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.build(e);
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (Exception e) {
          // Ignore
        }
        start();
      }
      return ((StorageGlobalContainer) globalContainer).getSyncStatus();
    } finally {
      rwl.writeLock().unlock();
    }
  }

  private void checkConnectedMode() {
    if (!isConnectedMode()) {
      throw new UnsupportedOperationException("Requires to be in connected mode");
    }
  }

  private boolean isConnectedMode() {
    return globalConfig.getServerId() != null;
  }

  @Override
  public ValidationResult validateCredentials(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    checkConnectedMode();
    rwl.readLock().lock();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      connectedContainer.startComponents();
      return connectedContainer.validateCredentials();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.build(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      rwl.readLock().unlock();
    }
  }

  @Override
  public Map<String, RemoteModule> allModulesByKey() {
    checkConnectedMode();
    rwl.readLock().lock();
    try {
      return ((StorageGlobalContainer) globalContainer).allModulesByKey();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.build(e);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void syncModule(ServerConfiguration serverConfig, String moduleKey) {
    checkNotNull(serverConfig);
    checkConnectedMode();
    rwl.writeLock().lock();
    changeState(State.SYNCING);
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      connectedContainer.startComponents();
      connectedContainer.syncModule(moduleKey);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.build(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(((StorageGlobalContainer) globalContainer).getSyncStatus() != null ? State.SYNCED : State.NOT_SYNCED);
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ModuleSyncStatus getModuleSyncStatus(String moduleKey) {
    checkConnectedMode();
    rwl.readLock().lock();
    try {
      return ((StorageGlobalContainer) globalContainer).getModuleSyncStatus(moduleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void stop() {
    rwl.writeLock().lock();
    try {
      globalContainer.stopComponents(false);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.build(e);
    } finally {
      this.globalContainer = null;
      changeState(State.UNKNOW);
      rwl.writeLock().unlock();
    }
  }

}
