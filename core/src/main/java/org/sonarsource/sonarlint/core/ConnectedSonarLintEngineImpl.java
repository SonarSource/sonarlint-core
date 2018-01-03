/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageContainerHandler;
import org.sonarsource.sonarlint.core.util.LoggedErrorHandler;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static com.google.common.base.Preconditions.checkNotNull;

public final class ConnectedSonarLintEngineImpl implements ConnectedSonarLintEngine {

  private static final Logger LOG = Loggers.get(ConnectedSonarLintEngineImpl.class);

  private final ConnectedGlobalConfiguration globalConfig;
  private StorageContainer storageContainer;
  private final ReadWriteLock rwl = new ReentrantReadWriteLock();
  private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
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
    stateListeners.add(listener);
  }

  @Override
  public void removeStateListener(StateListener listener) {
    stateListeners.remove(listener);
  }

  private void changeState(State state) {
    this.state = state;
    for (StateListener listener : stateListeners) {
      listener.stateChanged(state);
    }
  }

  private StorageContainerHandler getHandler() {
    if (storageContainer == null) {
      throw new IllegalStateException("SonarLint Engine for server '" + globalConfig.getServerId() + "' is stopped.");
    }
    return storageContainer.getHandler();
  }

  public StorageContainer getGlobalContainer() {
    return storageContainer;
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();
    storageContainer = StorageContainer.create(globalConfig);
    try {
      storageContainer.startComponents();
      if (getHandler().getGlobalStorageStatus() == null) {
        changeState(State.NEVER_UPDATED);
      } else if (getHandler().getGlobalStorageStatus().isStale()) {
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

  private void setLogging(@Nullable LogOutput logOutput) {
    if (logOutput != null) {
      Loggers.setTarget(logOutput);
    } else {
      Loggers.setTarget(this.logOutput);
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    checkNotNull(configuration);
    checkNotNull(issueListener);
    setLogging(logOutput);
    LoggedErrorHandler errorHandler = new LoggedErrorHandler(configuration.inputFiles());
    Loggers.setErrorHandler(errorHandler);
    return withReadLock(() -> {
      try {
        AnalysisResults results = getHandler().analyze(storageContainer.getGlobalExtensionContainer(), configuration, issueListener, new ProgressWrapper(monitor));
        errorHandler.getErrorFiles().forEach(results.failedAnalysisFiles()::add);
        return results;
      } catch (RuntimeException e) {
        throw SonarLintWrappedException.wrap(e);
      }
    });

  }

  @Override
  public GlobalStorageStatus getGlobalStorageStatus() {
    return withRwLock(getHandler()::getGlobalStorageStatus);
  }

  @Override
  public UpdateResult update(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    setLogging(null);
    return withRwLock(() -> {
      stop(false);
      changeState(State.UPDATING);
      List<SonarAnalyzer> analyzers;
      try {
        analyzers = runInConnectedContainer(serverConfig, container -> container.update(new ProgressWrapper(monitor)));
      } finally {
        start();
      }
      return new UpdateResult(getHandler().getGlobalStorageStatus(), analyzers);
    });
  }

  @Override
  public RuleDetails getRuleDetails(String ruleKey) {
    return withReadLock(() -> getHandler().getRuleDetails(ruleKey));
  }

  @Override
  public Collection<LoadedAnalyzer> getLoadedAnalyzers() {
    return withReadLock(() -> getHandler().getAnalyzers());
  }

  @Override
  public StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    return withReadLock(() -> runInConnectedContainer(serverConfig, container -> container.checkForUpdate(new ProgressWrapper(monitor))));
  }

  @Override
  public StorageUpdateCheckResult checkIfModuleStorageNeedUpdate(ServerConfiguration serverConfig, String moduleKey, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    checkNotNull(moduleKey);
    return withReadLock(() -> runInConnectedContainer(serverConfig, container -> container.checkForUpdate(moduleKey, new ProgressWrapper(monitor))));
  }

  @Override
  public Map<String, RemoteModule> allModulesByKey() {
    return withReadLock(() -> getHandler().allModulesByKey());
  }

  @Override
  public Map<String, RemoteModule> downloadAllModules(ServerConfiguration serverConfig, @Nullable ProgressMonitor monitor) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getHandler().downloadModuleList(serverConfig, new ProgressWrapper(monitor));
    });
  }

  private void checkUpdateStatus() {
    if (state != State.UPDATED) {
      throw new GlobalUpdateRequiredException("Please update server '" + globalConfig.getServerId() + "'");
    }
  }

  @Override
  public List<ServerIssue> getServerIssues(String moduleKey, String filePath) {
    return withReadLock(() -> getHandler().getServerIssues(moduleKey, filePath));
  }

  @Override
  public Set<String> getExcludedFiles(String moduleKey, Collection<String> filePaths, Predicate<String> testFilePredicate) {
    return withReadLock(() -> getHandler().getExcludedFiles(moduleKey, filePaths, testFilePredicate));
  }

  @Override
  public List<ServerIssue> downloadServerIssues(ServerConfiguration serverConfig, String moduleKey, String filePath) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getHandler().downloadServerIssues(serverConfig, moduleKey, filePath);
    });
  }

  @Override
  public void downloadServerIssues(ServerConfiguration serverConfig, String moduleKey) {
    withRwLock(() -> {
      getHandler().downloadServerIssues(serverConfig, moduleKey);
      return null;
    });
  }

  @Override
  public void updateModule(ServerConfiguration serverConfig, String moduleKey, @Nullable ProgressMonitor monitor) {
    checkNotNull(serverConfig);
    checkNotNull(moduleKey);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateModule(moduleKey, new ProgressWrapper(monitor));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(getHandler().getGlobalStorageStatus() != null ? State.UPDATED : State.NEVER_UPDATED);
      rwl.writeLock().unlock();
    }
  }

  @Override
  public ModuleStorageStatus getModuleStorageStatus(String moduleKey) {
    checkNotNull(moduleKey);
    return withReadLock(() -> getHandler().getModuleStorageStatus(moduleKey), false);
  }

  @Override
  public void stop(boolean deleteStorage) {
    setLogging(null);
    rwl.writeLock().lock();
    try {
      if (storageContainer == null) {
        return;
      }
      if (deleteStorage) {
        getHandler().deleteStorage();
      }
      storageContainer.stopComponents(false);
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      this.storageContainer = null;
      changeState(State.UNKNOW);
      rwl.writeLock().unlock();
    }
  }

  private <U> U runInConnectedContainer(ServerConfiguration serverConfig, Function<ConnectedContainer, U> func) {
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, serverConfig);
    try {
      connectedContainer.startComponents();
      return func.apply(connectedContainer);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
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
    return withReadLock(callable, true);
  }

  private <T> T withReadLock(Supplier<T> callable, boolean checkUpdateStatus) {
    setLogging(null);
    rwl.readLock().lock();
    try {
      if (checkUpdateStatus) {
        checkUpdateStatus();
      }
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      rwl.readLock().unlock();
    }
  }
}
