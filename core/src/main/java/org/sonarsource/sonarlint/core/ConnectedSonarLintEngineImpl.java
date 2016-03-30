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
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageGlobalContainer;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConnectedSonarLintEngineImpl implements ConnectedSonarLintEngine {

  private final ConnectedGlobalConfiguration globalConfig;
  private StorageGlobalContainer globalContainer;
  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final List<StateListener> listeners = new ArrayList<>();
  private State state = State.UNKNOW;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    this.globalConfig = globalConfig;
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
    rwl.writeLock().lock();
    this.globalContainer = StorageGlobalContainer.create(globalConfig);
    try {
      globalContainer.startComponents();
      changeState(globalContainer.getUpdateStatus() != null ? State.UPDATED : State.NEVER_UPDATED);
    } catch (RuntimeException e) {
      changeState(State.UNKNOW);
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    rwl.readLock().lock();
    try {
      checkUpdateStatus();
      return getGlobalContainer().getRuleDetails(ruleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener) {
    checkNotNull(configuration);
    checkNotNull(issueListener);
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
    rwl.readLock().lock();
    try {
      return getGlobalContainer().getUpdateStatus();
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalUpdateStatus update(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    rwl.writeLock().lock();
    stop(false);
    changeState(State.UPDATING);
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      try {
        connectedContainer.startComponents();
        connectedContainer.update();
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
  public ValidationResult validateConnection(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    rwl.readLock().lock();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      connectedContainer.startComponents();
      return connectedContainer.validateConnection();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
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
    rwl.readLock().lock();
    try {
      return getGlobalContainer().getModuleUpdateStatus(moduleKey);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public void stop(boolean deleteStorage) {
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
