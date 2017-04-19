/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectId;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageContainer;
import org.sonarsource.sonarlint.core.log.SonarLintLogging;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConnectedSonarLintEngineImpl implements ConnectedSonarLintEngine {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectedSonarLintEngineImpl.class);

  private final ConnectedGlobalConfiguration globalConfig;
  private StorageContainer globalContainer;
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

  public StorageContainer getGlobalContainer() {
    if (globalContainer == null) {
      throw new IllegalStateException("SonarLint Engine for server '" + globalConfig.getServerId() + "' is stopped.");
    }
    return globalContainer;
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();
    this.globalContainer = StorageContainer.create(globalConfig);
    try {
      globalContainer.startComponents();
      if (globalContainer.getGlobalStorageStatus() == null) {
        changeState(State.NEVER_UPDATED);
      } else if (globalContainer.getGlobalStorageStatus().isStale()) {
        changeState(State.NEED_UPDATE);
      } else {
        changeState(State.UPDATED);
      }
    } catch (StorageException e) {
      LOG.debug(e.getMessage(), e);
      changeState(State.NEED_UPDATE);
    } catch (RuntimeException e) {
      changeState(State.UNKNOW);
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public RuleDetails getRuleDetails(@Nullable String organizationKey, String ruleKey) {
    return withReadLock(() -> {
      checkUpdateStatus();
      return getGlobalContainer().getRuleDetails(ruleKey);
    });
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
    LoggedErrorHandler errorHandler = new LoggedErrorHandler(configuration.inputFiles());
    SonarLintLogging.setErrorHandler(errorHandler);
    rwl.readLock().lock();
    try {
      checkUpdateStatus();
      AnalysisResults results = getGlobalContainer().analyze(configuration, issueListener);
      errorHandler.getErrorFiles().forEach(results.failedAnalysisFiles()::add);
      return results;
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }

  @Override
  public GlobalStorageStatus getGlobalStorageStatus() {
    return withRwLock(getGlobalContainer()::getGlobalStorageStatus);
  }

  @Override
  public GlobalStorageStatus updateGlobalStorage(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
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
      return getGlobalContainer().getGlobalStorageStatus();
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(ServerConfiguration serverConfig, ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    return withReadLock(() -> {
      checkUpdateStatus();
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
        return connectedContainer.checkForUpdate(new ProgressWrapper(monitor));
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (Exception e) {
          // Ignore
        }
      }
    });
  }

  @Override
  public StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(ServerConfiguration serverConfig, ProjectId projectId, ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    checkNotNull(projectId);
    return withReadLock(() -> {
      checkUpdateStatus();
      ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
      try {
        connectedContainer.startComponents();
        return connectedContainer.checkForUpdate(projectId, new ProgressWrapper(monitor));
      } finally {
        try {
          connectedContainer.stopComponents(false);
        } catch (Exception e) {
          // Ignore
        }
      }
    });
  }

  @Override
  public Map<String, RemoteModule> allModulesByKey() {
    return withReadLock(() -> {
      checkUpdateStatus();
      return getGlobalContainer().allModulesByKey();
    });
  }

  @Override
  public Map<String, RemoteModule> downloadAllModules(ServerConfiguration serverConfig) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getGlobalContainer().downloadModuleList(serverConfig);
    });
  }

  private void checkUpdateStatus() {
    if (state != State.UPDATED) {
      throw new GlobalUpdateRequiredException("Please update server '" + globalConfig.getServerId() + "'");
    }
  }

  @Override
  public List<ServerIssue> getServerIssues(ProjectId projectId, String filePath) {
    return withReadLock(() -> {
      checkUpdateStatus();
      return getGlobalContainer().getServerIssues(projectId, filePath);
    });
  }

  @Override
  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, ProjectId projectId, String filePath) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getGlobalContainer().downloadServerIssues(serverConfig, projectId, filePath);
    });
  }

  @Override
  public void downloadServerIssues(ServerConfiguration serverConfig, ProjectId projectId) {
    withRwLock(() -> {
      checkUpdateStatus();
      getGlobalContainer().downloadServerIssues(serverConfig, projectId);
      return null;
    });
  }

  @Override
  public void updateProjectStorage(ServerConfiguration serverConfig, ProjectId projectId, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    checkNotNull(projectId);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateModule(projectId);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(getGlobalContainer().getGlobalStorageStatus() != null ? State.UPDATED : State.NEVER_UPDATED);
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ProjectStorageStatus getProjectStorageStatus(ProjectId projectId) {
    checkNotNull(projectId);
    return withReadLock(() -> getGlobalContainer().getProjectStorageStatus(projectId));
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

  private <T> T withRwLock(Supplier<T> callable) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  private <T> T withReadLock(Supplier<T> callable) {
    setLogging(null);
    rwl.readLock().lock();
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }
}
