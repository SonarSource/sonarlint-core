/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedRuleDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.GlobalStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectBinding;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.StateListener;
import org.sonarsource.sonarlint.core.client.api.connected.StorageUpdateCheckResult;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.storage.StorageContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageContainerHandler;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.project.ServerProject;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static java.util.Objects.requireNonNull;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private static final Logger LOG = Loggers.get(ConnectedSonarLintEngineImpl.class);

  private final ConnectedGlobalConfiguration globalConfig;
  private StorageContainer storageContainer;
  private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
  private volatile State state = State.UNKNOWN;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
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
    return getGlobalContainer().getHandler();
  }

  public StorageContainer getGlobalContainer() {
    if (storageContainer == null) {
      throw new IllegalStateException("SonarLint Engine for connection '" + globalConfig.getConnectionId() + "' is stopped.");
    }
    return storageContainer;
  }

  @Override
  protected ModuleRegistry getModuleRegistry() {
    return getGlobalContainer().getModuleRegistry();
  }

  public void start() {
    setLogging(null);
    rwl.writeLock().lock();
    storageContainer = new StorageContainer(globalConfig);
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
      LOG.error("Unable to start the SonarLint engine", e);
      changeState(State.UNKNOWN);
    } finally {
      rwl.writeLock().unlock();
    }
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable LogOutput logOutput, @Nullable ProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    return withReadLock(() -> {
      setLogging(logOutput);
      return withModule(configuration, moduleContainer -> {
        try {
          return getHandler().analyze(moduleContainer, configuration, issueListener, new ProgressWrapper(monitor));
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.wrap(e);
        }
      });

    });
  }

  @Override
  public GlobalStorageStatus getGlobalStorageStatus() {
    return withRwLock(getHandler()::getGlobalStorageStatus);
  }

  @Override
  public UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor) {
    requireNonNull(endpoint);
    setLogging(null);
    return withRwLock(() -> {
      stop(false);
      changeState(State.UPDATING);
      List<SonarAnalyzer> analyzers;
      try {
        analyzers = runInConnectedContainer(endpoint, client, container -> container.update(new ProgressWrapper(monitor)));
      } finally {
        start();
      }
      return new UpdateResult(getHandler().getGlobalStorageStatus(), analyzers);
    });
  }

  @Override
  public ConnectedRuleDetails getRuleDetails(String ruleKey) {
    return withReadLock(() -> getGlobalContainer().getRuleDetails(ruleKey));
  }

  @Override
  public ConnectedRuleDetails getActiveRuleDetails(String ruleKey, @Nullable String projectKey) {
    return withReadLock(() -> getGlobalContainer().getRuleDetails(ruleKey, projectKey));
  }

  @Override
  public Collection<PluginDetails> getPluginDetails() {
    return withReadLock(() -> getHandler().getPluginDetails());
  }

  @Override
  public StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor) {
    requireNonNull(endpoint);
    return withReadLock(() -> runInConnectedContainer(endpoint, client, container -> container.checkForUpdate(new ProgressWrapper(monitor))));
  }

  @Override
  public StorageUpdateCheckResult checkIfProjectStorageNeedUpdate(EndpointParams endpoint, HttpClient client, String projectKey,
    @Nullable ProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    return withReadLock(() -> runInConnectedContainer(endpoint, client, container -> container.checkForUpdate(projectKey, new ProgressWrapper(monitor))));
  }

  @Override
  public Map<String, ServerProject> allProjectsByKey() {
    return withReadLock(() -> getHandler().allProjectsByKey());
  }

  @Override
  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ProgressMonitor monitor) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getHandler().downloadProjectList(endpoint, client, new ProgressWrapper(monitor));
    });
  }

  private void checkUpdateStatus() {
    if (state != State.UPDATED) {
      throw new GlobalStorageUpdateRequiredException(globalConfig.getConnectionId());
    }
  }

  @Override
  public List<ServerIssue> getServerIssues(ProjectBinding projectBinding, String ideFilePath) {
    return withReadLock(() -> getHandler().getServerIssues(projectBinding, ideFilePath));
  }

  @Override
  public <G> List<G> getExcludedFiles(ProjectBinding projectBinding, Collection<G> files, Function<G, String> ideFilePathExtractor, Predicate<G> testFilePredicate) {
    return withReadLock(() -> getHandler().getExcludedFiles(projectBinding, files, ideFilePathExtractor, testFilePredicate));
  }

  @Override
  public List<ServerIssue> downloadServerIssues(EndpointParams endpoint, HttpClient client, ProjectBinding projectBinding, String ideFilePath,
    boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getHandler().downloadServerIssues(endpoint, client, projectBinding, ideFilePath, fetchTaintVulnerabilities, new ProgressWrapper(monitor));
    });
  }

  @Override
  public void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor) {
    withRwLock(() -> {
      getHandler().downloadServerIssues(endpoint, client, projectKey, fetchTaintVulnerabilities, new ProgressWrapper(monitor));
      return null;
    });
  }

  @Override
  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    return withReadLock(() -> getHandler().calculatePathPrefixes(projectKey, ideFilePaths));
  }

  @Override
  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, endpoint, client);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateProject(projectKey, fetchTaintVulnerabilities, new ProgressWrapper(monitor));
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
  public ProjectStorageStatus getProjectStorageStatus(String projectKey) {
    requireNonNull(projectKey);
    return withReadLock(() -> getHandler().getProjectStorageStatus(projectKey), false);
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
      changeState(State.UNKNOWN);
      rwl.writeLock().unlock();
    }
  }

  private <U> U runInConnectedContainer(EndpointParams endpoint, HttpClient client, Function<ConnectedContainer, U> func) {
    ConnectedContainer connectedContainer = new ConnectedContainer(globalConfig, endpoint, client);
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
