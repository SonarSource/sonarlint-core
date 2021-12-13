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

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
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
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;
import org.sonarsource.sonarlint.core.client.api.exceptions.GlobalStorageUpdateRequiredException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.StorageException;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ClientProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.ConnectedContainer;
import org.sonarsource.sonarlint.core.container.connected.update.ProjectListDownloader;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.storage.GlobalStores;
import org.sonarsource.sonarlint.core.container.storage.GlobalUpdateStatusReader;
import org.sonarsource.sonarlint.core.container.storage.StorageContainer;
import org.sonarsource.sonarlint.core.container.storage.StorageContainerHandler;
import org.sonarsource.sonarlint.core.plugin.cache.PluginCache;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository;
import org.sonarsource.sonarlint.core.plugin.commons.PluginInstancesRepository.Configuration;
import org.sonarsource.sonarlint.core.plugin.commons.loading.PluginLocation;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.storage.LocalStorageSynchronizer;
import org.sonarsource.sonarlint.core.storage.ProjectStorage;

import static java.util.Objects.requireNonNull;
import static org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths.encodeForFs;

public final class ConnectedSonarLintEngineImpl extends AbstractSonarLintEngine implements ConnectedSonarLintEngine {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectedGlobalConfiguration globalConfig;
  private final GlobalUpdateStatusReader globalStatusReader;
  private StorageContainer storageContainer;
  private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
  private volatile State state = State.UNKNOWN;
  private final GlobalStores globalStores;
  private final ProjectStorage projectStorage;
  private final LocalStorageSynchronizer storageSynchronizer;

  private boolean containerStarted;

  public ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration globalConfig) {
    super(globalConfig.getLogOutput());
    this.globalConfig = globalConfig;
    this.globalStores = new GlobalStores(globalConfig);
    this.globalStatusReader = new GlobalUpdateStatusReader(globalStores.getServerInfoStore(), globalStores.getStorageStatusStore());

    Path storageRoot = globalConfig.getStorageRoot().resolve(encodeForFs(globalConfig.getConnectionId()));
    Path projectsStorageRoot = storageRoot.resolve("projects");
    projectStorage = new ProjectStorage(projectsStorageRoot);
    storageSynchronizer = new LocalStorageSynchronizer(globalConfig.getEnabledLanguages(), projectStorage);
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

    try {
      var pluginInstancesRepository = createPluginInstanceRepository();
      storageContainer = new StorageContainer(globalConfig, this.globalStores, projectStorage, globalStatusReader, pluginInstancesRepository);
      storageContainer.startComponents();
      containerStarted = true;
      var globalStorageStatus = globalStatusReader.read();
      if (globalStorageStatus == null) {
        changeState(State.NEVER_UPDATED);
      } else if (globalStorageStatus.isStale()) {
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

  private PluginInstancesRepository createPluginInstanceRepository() {
    Path cacheDir = globalConfig.getSonarLintUserHome().resolve("plugins");
    var fileCache = PluginCache.create(cacheDir);

    var pluginReferenceStore = globalStores.getPluginReferenceStore();
    List<PluginLocation> plugins = new ArrayList<>();
    Map<String, URL> extraPluginsUrlsByKey = globalConfig.getExtraPluginsUrlsByKey();
    Map<String, URL> embeddedPluginsUrlsByKey = globalConfig.getEmbeddedPluginUrlsByKey();

    Sonarlint.PluginReferences protoReferences;
    try {
      protoReferences = pluginReferenceStore.getAll();
    } catch (StorageException e) {
      LOG.debug("Unable to read plugins references from storage", e);
      protoReferences = Sonarlint.PluginReferences.newBuilder().build();
    }
    protoReferences.getReferenceList().forEach(r -> {
      if (embeddedPluginsUrlsByKey.containsKey(r.getKey())) {
        var ref = fileCache.getFromCacheOrCopy(embeddedPluginsUrlsByKey.get(r.getKey()));
        var jarPath = Objects.requireNonNull(fileCache.get(ref.getFilename(), r.getHash()), "Error reading plugin from cache");
        plugins.add(new PluginLocation(jarPath, true));
      } else {
        var jarPath = fileCache.get(r.getFilename(), r.getHash());
        if (jarPath == null) {
          throw new StorageException("The plugin " + r.getFilename() + " was not found in the local storage.");
        }
        plugins.add(new PluginLocation(jarPath, false));
      }
    });
    extraPluginsUrlsByKey.values().stream().map(fileCache::getFromCacheOrCopy).forEach(r -> {
      var jarPath = fileCache.get(r.getFilename(), r.getHash());
      plugins.add(new PluginLocation(jarPath, true));
    });

    var config = new Configuration(plugins, globalConfig.getEnabledLanguages(), Optional.ofNullable(globalConfig.getNodeJsVersion()));
    return new PluginInstancesRepository(config);
  }

  @Override
  public AnalysisResults analyze(ConnectedAnalysisConfiguration configuration, IssueListener issueListener, @Nullable ClientLogOutput logOutput,
    @Nullable ClientProgressMonitor monitor) {
    requireNonNull(configuration);
    requireNonNull(issueListener);
    return withReadLock(() -> {
      setLogging(logOutput);
      return withModule(configuration, moduleContainer -> {
        try {
          return getHandler().analyze(moduleContainer, configuration, issueListener, new ProgressMonitor(monitor));
        } catch (RuntimeException e) {
          throw SonarLintWrappedException.wrap(e);
        }
      });

    });
  }

  @Override
  public GlobalStorageStatus getGlobalStorageStatus() {
    return wrapErrors(globalStatusReader::read);
  }

  @Override
  public void sync(EndpointParams endpoint, HttpClient client, Set<String> projectKeys, @Nullable ClientProgressMonitor monitor) {
    var serverApi = new ServerApi(new ServerApiHelper(endpoint, client));
    storageSynchronizer.synchronize(serverApi, projectKeys, new ProgressMonitor(monitor));
  }

  @Override
  public UpdateResult update(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    setLogging(null);
    return withRwLock(() -> {
      stop(false);
      changeState(State.UPDATING);
      List<SonarAnalyzer> analyzers;
      try {
        analyzers = runInConnectedContainer(endpoint, client, container -> container.update(new ProgressMonitor(monitor)));
      } finally {
        start();
      }
      return new UpdateResult(globalStatusReader.read(), analyzers);
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
  public StorageUpdateCheckResult checkIfGlobalStorageNeedUpdate(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    return checkUpToDateThen(() -> runInConnectedContainer(endpoint, client,
      container -> container.checkForUpdate(new ProgressMonitor(monitor))));
  }

  @Override
  public Map<String, ServerProject> allProjectsByKey() {
    return checkUpToDateThen(() -> globalStores.getServerProjectsStore().getAll());
  }

  @Override
  public Map<String, ServerProject> downloadAllProjects(EndpointParams endpoint, HttpClient client, @Nullable ClientProgressMonitor monitor) {
    return wrapErrors(() -> {
      try {
        return new ProjectListDownloader(new ServerApiHelper(endpoint, client), globalStores.getServerProjectsStore()).fetch(new ProgressMonitor(monitor));
      } catch (Exception e) {
        // null as cause so that it doesn't get wrapped
        throw new DownloadException("Failed to update module list: " + e.getMessage(), null);
      }
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
    boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    return withRwLock(() -> {
      checkUpdateStatus();
      return getHandler().downloadServerIssues(endpoint, client, projectBinding, ideFilePath, fetchTaintVulnerabilities, new ProgressMonitor(monitor));
    });
  }

  @Override
  public void downloadServerIssues(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    withRwLock(() -> {
      getHandler().downloadServerIssues(endpoint, client, projectKey, fetchTaintVulnerabilities, new ProgressMonitor(monitor));
      return null;
    });
  }

  @Override
  public ProjectBinding calculatePathPrefixes(String projectKey, Collection<String> ideFilePaths) {
    return withReadLock(() -> getHandler().calculatePathPrefixes(projectKey, ideFilePaths));
  }

  @Override
  public void updateProject(EndpointParams endpoint, HttpClient client, String projectKey, boolean fetchTaintVulnerabilities, @Nullable ClientProgressMonitor monitor) {
    requireNonNull(endpoint);
    requireNonNull(projectKey);
    setLogging(null);
    rwl.writeLock().lock();
    checkUpdateStatus();
    var connectedContainer = new ConnectedContainer(globalConfig, globalStores, endpoint, client);
    try {
      changeState(State.UPDATING);
      connectedContainer.startComponents();
      connectedContainer.updateProject(projectKey, fetchTaintVulnerabilities, globalStatusReader.read(), new ProgressMonitor(monitor));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      try {
        connectedContainer.stopComponents(false);
      } catch (Exception e) {
        // Ignore
      }
      changeState(globalStatusReader.read() != null ? State.UPDATED : State.NEVER_UPDATED);
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
      if (deleteStorage) {
        globalStores.deleteAll();
      }
      if (storageContainer != null && containerStarted) {
        storageContainer.stopComponents(false);
      }
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    } finally {
      this.storageContainer = null;
      changeState(State.UNKNOWN);
      rwl.writeLock().unlock();
    }
  }

  private <U> U runInConnectedContainer(EndpointParams endpoint, HttpClient client, Function<ConnectedContainer, U> func) {
    var connectedContainer = new ConnectedContainer(globalConfig, globalStores, endpoint, client);
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

  private <T> T checkUpToDateThen(Supplier<T> callable) {
    setLogging(null);
    try {
      checkUpdateStatus();
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private <T> T wrapErrors(Supplier<T> callable) {
    setLogging(null);
    try {
      return callable.get();
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
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
