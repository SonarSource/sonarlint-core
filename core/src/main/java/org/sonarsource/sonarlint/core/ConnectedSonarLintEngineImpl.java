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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageGlobalContainer;
import org.sonarsource.sonarlint.core.log.SonarLintLogging;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConnectedSonarLintEngineImpl implements ConnectedSonarLintEngine {

  private final ConnectedGlobalConfiguration globalConfig;
  private StorageGlobalContainer globalContainer;
  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final List<StateListener> listeners = new CopyOnWriteArrayList<>();
  private volatile State state = State.UNKNOW;
  private LogOutput logOutput = null;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
    this.logOutput = globalConfig.getLogOutput();
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

  public StorageGlobalContainer getGlobalContainer() {
    if (globalContainer == null) {
      throw new IllegalStateException("SonarLint Engine for server '" + globalConfig.getServerId() + "' is stopped.");
    }
    return globalContainer;
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();
    this.globalContainer = StorageGlobalContainer.create(globalConfig);
    try {
      globalContainer.startComponents();
      if (globalContainer.getUpdateStatus() == null) {
        changeState(State.NEVER_UPDATED);
      } else if (globalContainer.getUpdateStatus().isStale()) {
        changeState(State.NEED_UPDATE);
      } else {
        changeState(State.UPDATED);
      }

    } catch (RuntimeException e) {
      changeState(State.UNKNOW);
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    setLogging(null);
    rwl.readLock().lock();
    try {
      checkUpdateStatus();
      return getGlobalContainer().getRuleDetails(ruleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  private void setLogging(@Nullable LogOutput logOutput) {
    if (logOutput != null) {
      SonarLintLogging.set(logOutput);
    } else {
      SonarLintLogging.set(this.logOutput);
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    return analyze(configuration, issueListener, null);
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput) {
    checkNotNull(configuration);
    checkNotNull(issueListener);
    setLogging(logOutput);
    rwl.readLock().lock();
    try {
      checkUpdateStatus();
      return getGlobalContainer().analyze(configuration, issueListener);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalUpdateStatus getUpdateStatus() {
    setLogging(null);
    rwl.readLock().lock();
    try {
      return getGlobalContainer().getUpdateStatus();
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalUpdateStatus update(ServerConfiguration serverConfig) {
    return update(serverConfig, null);
  }

  @Override
  public GlobalUpdateStatus update(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    setLogging(null);
    rwl.writeLock().lock();
    stop(false);
    changeState(State.UPDATING);
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      try {
        connectedContainer.startComponents();
        connectedContainer.update(new ProgressWrapper(monitor));
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.wrap(e);
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (Exception e) {
          // Ignore
        }
        start();
      }
      return getGlobalContainer().getUpdateStatus();
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public Map<String, RemoteModule> allModulesByKey() {
    setLogging(null);
    rwl.readLock().lock();
    try {
      checkUpdateStatus();
      return getGlobalContainer().allModulesByKey();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }

  private void checkUpdateStatus() {
    if (state != State.UPDATED) {
      throw new GlobalUpdateRequiredException("Please update server '" + globalConfig.getServerId() + "'");
    }
  }

  @Override
  public void updateModule(ServerConfiguration serverConfig, String moduleKey) {
    checkNotNull(serverConfig);
    checkNotNull(moduleKey);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateModule(moduleKey);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(getGlobalContainer().getUpdateStatus() != null ? State.UPDATED : State.NEVER_UPDATED);
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ModuleUpdateStatus getModuleUpdateStatus(String moduleKey) {
    checkNotNull(moduleKey);
    setLogging(null);
    rwl.readLock().lock();
    try {
      return getGlobalContainer().getModuleUpdateStatus(moduleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void stop(boolean deleteStorage) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      if (globalContainer == null) {
        return;
      }
      if (deleteStorage) {
        globalContainer.deleteStorage();
      }
      globalContainer.stopComponents(false);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      this.globalContainer = null;
      changeState(State.UNKNOW);
      rwl.writeLock().unlock();
    }
  }
}
