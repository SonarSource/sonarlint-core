/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;
import org.sonarsource.sonarlint.core.analysis.NodeJsService;
import org.sonarsource.sonarlint.core.commons.ConnectionKind;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.commons.LoadedPlugins;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ArtifactsLoadingResult;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ConnectedArtifactsLoadingStrategyFactory;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.ResolutionPlan;
import org.sonarsource.sonarlint.core.plugin.loading.strategy.StandaloneArtifactsLoadingStrategy;
import org.sonarsource.sonarlint.core.plugin.skipped.SkippedPluginsRepository;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactOrigin;
import org.sonarsource.sonarlint.core.plugin.source.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.source.PendingDownload;
import org.sonarsource.sonarlint.core.plugin.source.ResolvedArtifact;
import org.sonarsource.sonarlint.core.plugin.source.binaries.BinariesArtifactSource;
import org.sonarsource.sonarlint.core.repository.connection.AbstractConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredPlugin;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.PluginsSynchronizedEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginsServiceTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final Path ossPath = Paths.get("folder", "oss");
  private static final Path enterprisePath = Paths.get("folder", "enterprise");
  private PluginsService underTest;
  private PluginsRepository pluginsRepository;
  private ConnectionConfigurationRepository connectionConfigurationStorage;
  private StorageService storageService;
  private ConnectionStorage connectionStorage;
  private ServerInfoStorage serverInfoStorage;
  private PluginsStorage pluginStorage;
  private InitializeParams initializeParams;
  private ApplicationEventPublisher eventPublisher;
  private ConnectedArtifactsLoadingStrategyFactory connectedArtifactsLoadingStrategyFactory;
  private LanguageSupportRepository languageSupportRepository;

  @BeforeEach
  void prepare() {
    pluginsRepository = mock(PluginsRepository.class);
    storageService = mock(StorageService.class);
    connectionConfigurationStorage = mock(ConnectionConfigurationRepository.class);
    connectionStorage = mock(ConnectionStorage.class);
    serverInfoStorage = mock(ServerInfoStorage.class);
    pluginStorage = mock(PluginsStorage.class);
    when(connectionStorage.plugins()).thenReturn(pluginStorage);
    initializeParams = mock(InitializeParams.class);
    when(initializeParams.getDisabledPluginKeysForAnalysis()).thenReturn(Set.of());
    eventPublisher = mock(ApplicationEventPublisher.class);
    when(pluginStorage.getStoredPluginsByKey()).thenReturn(Map.of());

    var standaloneArtifactsLoadingStrategy = mock(StandaloneArtifactsLoadingStrategy.class);
    connectedArtifactsLoadingStrategyFactory = mock(ConnectedArtifactsLoadingStrategyFactory.class);
    var connectedArtifactsLoadingStrategy = mock(ConnectedArtifactsLoadingStrategy.class);

    var csharpArtifact = new ResolvedArtifact(ArtifactState.ACTIVE, ossPath, ArtifactOrigin.EMBEDDED, null);
    when(standaloneArtifactsLoadingStrategy.resolveArtifacts()).thenReturn(new ResolutionPlan(new ArtifactsLoadingResult(Map.of("csharp", csharpArtifact)), List.of()));
    when(connectedArtifactsLoadingStrategy.resolveArtifacts()).thenReturn(new ResolutionPlan(new ArtifactsLoadingResult(Map.of("csharp", csharpArtifact)), List.of()));
    when(connectedArtifactsLoadingStrategyFactory.getOrCreate(any())).thenReturn(connectedArtifactsLoadingStrategy);

    var binariesArtifactSource = mock(BinariesArtifactSource.class);
    when(binariesArtifactSource.getOmnisharpExtraProperties()).thenReturn(Map.of());

    languageSupportRepository = mock(LanguageSupportRepository.class);

    underTest = createService(standaloneArtifactsLoadingStrategy, binariesArtifactSource, mock(ExecutorService.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_caches_and_publishes_statuses_before_start_even_for_immediate_completion(boolean connected) throws Exception {
    var callbackTasks = new ArrayList<Runnable>();
    var executor = mock(ExecutorService.class);
    doAnswer(inv -> captureTask(callbackTasks, inv)).when(executor).execute(any());
    var statusesPublished = new AtomicBoolean();
    doAnswer(inv -> markStatusesPublished(statusesPublished)).when(eventPublisher).publishEvent(any(PluginStatusesChangedEvent.class));
    var pending = new PendingDownload("java", () -> startAfterStatusPublication(connected, statusesPublished));
    configureDownloads(pending, executor);

    var config = connected ? underTest.getPlugins("conn") : underTest.getEmbeddedPlugins();

    assertThat(cachedPlugins(connected)).isSameAs(config);
    verify(eventPublisher, never()).publishEvent(any(PluginsSynchronizedEvent.class));
    assertThat(callbackTasks).hasSize(1);
    try (var callbackExecutor = Executors.newSingleThreadExecutor()) {
      callbackExecutor.submit(callbackTasks.getFirst()).get(5, TimeUnit.SECONDS);
    }
    verify(eventPublisher).publishEvent(any(PluginsSynchronizedEvent.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_starts_downloads_even_when_status_publication_throws(boolean connected) {
    var started = new AtomicBoolean();
    configureDownloads(new PendingDownload("java", () -> recordDownloadStart(started)), mock(ExecutorService.class));
    doThrow(new IllegalStateException("status listener failed")).when(eventPublisher).publishEvent(any(PluginStatusesChangedEvent.class));

    ThrowingCallable loadPlugins = connected ? () -> underTest.getPlugins("conn") : underTest::getEmbeddedPlugins;
    assertThatThrownBy(loadPlugins).isInstanceOf(IllegalStateException.class).hasMessage("status listener failed");
    assertThat(started).isTrue();
    assertThat(cachedPlugins(connected).downloads().completion()).isCompletedWithValue(null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_immediate_download_on_another_thread_cannot_leave_stale_cached_status(boolean connected) throws Exception {
    try (var executor = Executors.newCachedThreadPool()) {
      configureDownloads(new PendingDownload("java", () -> completeOnExecutor(executor)), executor);
      var reloaded = new CountDownLatch(1);
      doAnswer(inv -> unloadAfterDownload(connected, reloaded)).when(eventPublisher).publishEvent(any(PluginsSynchronizedEvent.class));

      var initial = underTest.getPluginStatuses(connected ? "conn" : null);
      var expectedInitial = PluginStatus.forLanguage(SonarLanguage.JAVA, ArtifactState.DOWNLOADING, null, null, null, null, null);
      assertThat(initial).contains(expectedInitial);
      assertThat(reloaded.await(5, TimeUnit.SECONDS)).isTrue();
      var expectedCompleted = PluginStatus.forLanguage(SonarLanguage.JAVA, ArtifactState.ACTIVE, ArtifactOrigin.ON_DEMAND, null, null, null, null);
      assertThat(underTest.getPluginStatuses(connected ? "conn" : null)).contains(expectedCompleted);
    }
  }

  private static Object captureTask(List<Runnable> tasks, InvocationOnMock invocation) {
    tasks.add(invocation.getArgument(0));
    return null;
  }

  private static Object markStatusesPublished(AtomicBoolean published) {
    published.set(true);
    return null;
  }

  private CompletableFuture<Void> startAfterStatusPublication(boolean connected, AtomicBoolean published) {
    assertThat(cachedPlugins(connected)).isNotNull();
    assertThat(published).isTrue();
    return CompletableFuture.completedFuture(null);
  }

  private static CompletableFuture<Void> recordDownloadStart(AtomicBoolean started) {
    started.set(true);
    return CompletableFuture.completedFuture(null);
  }

  private static CompletableFuture<Void> completeOnExecutor(ExecutorService executor) {
    return CompletableFuture.supplyAsync(() -> null, executor);
  }

  private Object unloadAfterDownload(boolean connected, CountDownLatch reloaded) {
    if (connected) {
      pluginsRepository.unload("conn");
    } else {
      pluginsRepository.unloadEmbedded();
    }
    reloaded.countDown();
    return null;
  }

  private PluginsConfiguration cachedPlugins(boolean connected) {
    return connected ? pluginsRepository.getPlugins("conn") : pluginsRepository.getEmbeddedPlugins();
  }

  private void configureDownloads(PendingDownload pending, ExecutorService executor) {
    pluginsRepository = new PluginsRepository();
    mockNoConnection("conn");
    var initial = new ResolutionPlan(new ArtifactsLoadingResult(Map.of("java", new ResolvedArtifact(ArtifactState.DOWNLOADING, null, null, null))), List.of(pending));
    var completed = new ResolutionPlan(new ArtifactsLoadingResult(Map.of("java", new ResolvedArtifact(ArtifactState.ACTIVE, null, ArtifactOrigin.ON_DEMAND, null))), List.of());
    var standalone = mock(StandaloneArtifactsLoadingStrategy.class);
    var connectedStrategy = mock(ConnectedArtifactsLoadingStrategy.class);
    when(standalone.resolveArtifacts()).thenReturn(initial, completed);
    when(connectedStrategy.resolveArtifacts()).thenReturn(initial, completed);
    when(connectedArtifactsLoadingStrategyFactory.getOrCreate("conn")).thenReturn(connectedStrategy);
    when(languageSupportRepository.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(SonarLanguage.JAVA));
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(Set.of(SonarLanguage.JAVA));
    underTest = createService(standalone, mock(BinariesArtifactSource.class), executor);
  }

  private PluginsService createService(StandaloneArtifactsLoadingStrategy standalone, BinariesArtifactSource binaries, ExecutorService executor) {
    return new PluginsService(pluginsRepository, mock(SkippedPluginsRepository.class), storageService,
      initializeParams, connectionConfigurationStorage, mock(NodeJsService.class), eventPublisher,
      standalone, connectedArtifactsLoadingStrategyFactory, binaries, languageSupportRepository, executor);
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionDoesNotExist_returnsFalse() {
    var connectionId = "notExisting";
    mockNoConnection(connectionId);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToCloud_returnsTrue() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerThatDoesNotExistOnStorage_returnsFalse() {
    var connectionId = "SQS";
    var connection = createConnection(connectionId, ConnectionKind.SONARQUBE);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerWithoutEnterprisePlugin_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("2025.1"));

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsNotPresentOnTheServer_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin("otherPlugin");

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseCSharpAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsPresentOnTheServer_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID);

    var result = underTest.shouldUseEnterpriseCSharpAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionDoesNotExist_returnsFalse() {
    var connectionId = "notExisting";
    mockNoConnection(connectionId);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToCloud_returnsTrue() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerThatDoesNotExistOnStorage_returnsFalse() {
    var connectionId = "SQS";
    var connection = createConnection(connectionId, ConnectionKind.SONARQUBE);
    mockConnection(connection);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerWithoutEnterprisePlugin_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("2025.1"));

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsNotPresentOnTheServer_returnsFalse() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin("otherPlugin");

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isFalse();
  }

  @Test
  void shouldUseEnterpriseVbAnalyzer_connectionIsToServerWithRepackagedPluginAndPluginIsPresentOnTheServer_returnsTrue() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.VBNET_ENTERPRISE_PLUGIN_ID);

    var result = underTest.shouldUseEnterpriseVbAnalyzer(connectionId);

    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = Language.class, names = {"CS", "VBNET", "COBOL"})
  void getEmbeddedPlugins_extraProperties_ReturnsExpectedDotnetProperties(Language language) {
    mockEnabledLanguages(language);

    var props = underTest.getEmbeddedPlugins().extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString());
    if (language == Language.CS) {
      assertThat(props).containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "false");
    }
    if (language == Language.VBNET) {
      assertThat(props).containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "false");
    }
  }

  @Test
  void getPlugins_extraProperties_forCloud_fallsBackToOss_whenEnterpriseNotInStorage() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);
    mockEnabledLanguages(Language.CS);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_forCloud_ReturnsEnterpriseProperties() {
    var connectionId = "SQC";
    var connection = createConnection(connectionId, ConnectionKind.SONARCLOUD);
    mockConnection(connection);
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServerWithEnterprisePlugins_ReturnsEnterpriseProperties() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("2025.1"));
    mockPlugins(Map.of(
      PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath,
      PluginsService.VBNET_ENTERPRISE_PLUGIN_ID, enterprisePath));
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServerWithRepackagedCsharpPlugin_ReturnsEnterprisePropertiesForCsharp() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.CSHARP_ENTERPRISE_PLUGIN_ID, enterprisePath);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", enterprisePath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "true")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "false");
  }

  @Test
  void getPlugins_extraProperties_connectionIsToServerWithRepackagedVbPlugin_ReturnsEnterprisePropertiesForVb() {
    var connectionId = "SQS";
    mockConnection(connectionId, ConnectionKind.SONARQUBE, Version.create("10.8"));
    mockPlugin(PluginsService.VBNET_ENTERPRISE_PLUGIN_ID);
    mockEnabledLanguages(Language.CS, Language.VBNET);

    var props = underTest.getPlugins(connectionId).extraProperties();

    assertThat(props)
      .containsEntry("sonar.cs.internal.analyzerPath", ossPath.toString())
      .containsEntry("sonar.cs.internal.shouldUseCsharpEnterprise", "false")
      .containsEntry("sonar.cs.internal.shouldUseVbEnterprise", "true");
  }

  @Test
  void should_return_list_size_equal_to_sonar_language_values() {
    var connectionId = "connection1";
    mockNoConnection(connectionId);

    var result = underTest.getPluginStatuses(connectionId);

    assertThat(result).hasSize(SonarLanguage.values().length);
  }

  @Test
  void should_report_unsupported_for_disabled_language_with_embedded_plugin() {
    var artifact = new ResolvedArtifact(ArtifactState.ACTIVE, ossPath, ArtifactOrigin.EMBEDDED, Version.create("1.0"));
    mockStatuses(null, Map.of("python", artifact));

    assertThat(status(null, SonarLanguage.PYTHON)).isEqualTo(PluginStatus.unsupported(SonarLanguage.PYTHON));
  }

  @ParameterizedTest
  @EnumSource(value = ArtifactState.class, names = {"ACTIVE", "SYNCED", "DOWNLOADING", "FAILED"})
  void should_preserve_enabled_language_status_and_metadata(ArtifactState state) {
    var version = Version.create("1.0");
    var artifact = new ResolvedArtifact(state, ossPath, ArtifactOrigin.EMBEDDED, version);
    mockEnabledInStandalone(SonarLanguage.PYTHON);
    mockStatuses(null, Map.of("python", artifact));

    assertThat(status(null, SonarLanguage.PYTHON))
      .isEqualTo(PluginStatus.forLanguage(SonarLanguage.PYTHON, state, ArtifactOrigin.EMBEDDED, version, null, ossPath, null));
  }

  @Test
  void should_report_unsupported_for_disabled_language_in_connected_mode() {
    var artifact = new ResolvedArtifact(ArtifactState.SYNCED, ossPath, ArtifactOrigin.SONARQUBE_SERVER, null);
    mockStatuses("connection", Map.of("python", artifact));

    assertThat(status("connection", SonarLanguage.PYTHON)).isEqualTo(PluginStatus.unsupported(SonarLanguage.PYTHON));
  }

  @Test
  void should_use_connected_mode_languages_for_connection() {
    var artifact = new ResolvedArtifact(ArtifactState.SYNCED, ossPath, ArtifactOrigin.SONARQUBE_SERVER, null);
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(Set.of(SonarLanguage.PYTHON));
    mockStatuses("connection", Map.of("python", artifact));

    assertThat(status("connection", SonarLanguage.PYTHON))
      .isEqualTo(PluginStatus.forLanguage(SonarLanguage.PYTHON, ArtifactState.SYNCED, ArtifactOrigin.SONARQUBE_SERVER, null, null, ossPath, null));
  }

  @Test
  void should_report_premium_for_connected_only_language_without_plugin() {
    mockConnectedOnly(SonarLanguage.PYTHON);
    mockStatuses(null, Map.of());

    assertThat(status(null, SonarLanguage.PYTHON)).isEqualTo(PluginStatus.premium(SonarLanguage.PYTHON));
  }

  @Test
  void should_not_report_premium_in_connected_mode_for_language_without_plugin() {
    mockConnectedOnly(SonarLanguage.PYTHON);
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(Set.of(SonarLanguage.PYTHON));
    mockStatuses("connection", Map.of());

    assertThat(status("connection", SonarLanguage.PYTHON)).isEqualTo(PluginStatus.unsupported(SonarLanguage.PYTHON));
  }

  @Test
  void should_preserve_active_for_connected_only_language_with_embedded_plugin() {
    var artifact = new ResolvedArtifact(ArtifactState.ACTIVE, ossPath, ArtifactOrigin.EMBEDDED, null);
    mockConnectedOnly(SonarLanguage.PYTHON);
    mockStatuses(null, Map.of("python", artifact));

    assertThat(status(null, SonarLanguage.PYTHON))
      .isEqualTo(PluginStatus.forLanguage(SonarLanguage.PYTHON, ArtifactState.ACTIVE, ArtifactOrigin.EMBEDDED, null, null, ossPath, null));
  }

  @Test
  void should_not_share_enablement_between_languages_of_same_plugin() {
    var artifact = new ResolvedArtifact(ArtifactState.ACTIVE, ossPath, ArtifactOrigin.EMBEDDED, null);
    mockEnabledInStandalone(SonarLanguage.JS);
    mockStatuses(null, Map.of("javascript", artifact));

    assertThat(status(null, SonarLanguage.JS).state()).isEqualTo(ArtifactState.ACTIVE);
    assertThat(status(null, SonarLanguage.TS)).isEqualTo(PluginStatus.unsupported(SonarLanguage.TS));
  }

  @Test
  void should_not_share_premium_between_languages_of_same_plugin() {
    mockEnabledInStandalone(SonarLanguage.JS);
    mockConnectedOnly(SonarLanguage.TS);
    mockStatuses(null, Map.of());

    assertThat(status(null, SonarLanguage.JS)).isEqualTo(PluginStatus.unsupported(SonarLanguage.JS));
    assertThat(status(null, SonarLanguage.TS)).isEqualTo(PluginStatus.premium(SonarLanguage.TS));
  }

  @Test
  void should_publish_connected_mode_statuses_when_connection_plugins_are_loaded() {
    var connectionId = "SQC";
    mockConnection(createConnection(connectionId, ConnectionKind.SONARCLOUD));
    mockEnabledLanguages(Language.CS);
    when(languageSupportRepository.getEnabledLanguagesInConnectedMode()).thenReturn(Set.of(SonarLanguage.CS));
    var csharpStatus = PluginStatus.forLanguage(SonarLanguage.CS, ArtifactState.ACTIVE, ArtifactOrigin.EMBEDDED, null, null, ossPath, null);

    underTest.getPlugins(connectionId);

    verify(eventPublisher).publishEvent(new PluginStatusesChangedEvent(connectionId, allUnsupportedExcept(csharpStatus)));
  }

  private static List<PluginStatus> allUnsupportedExcept(PluginStatus status) {
    var statuses = new ArrayList<PluginStatus>();
    for (var language : SonarLanguage.values()) {
      statuses.add(language == status.language() ? status : PluginStatus.unsupported(language));
    }
    return statuses;
  }

  private void mockEnabledInStandalone(SonarLanguage... languages) {
    when(languageSupportRepository.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
  }

  private void mockConnectedOnly(SonarLanguage language) {
    when(languageSupportRepository.isEnabledOnlyInConnectedMode(language)).thenReturn(true);
  }

  private void mockStatuses(@Nullable String connectionId, Map<String, ResolvedArtifact> artifacts) {
    var configuration = new PluginsConfiguration(new ArtifactsLoadingResult(artifacts), mock(LoadedPlugins.class), Map.of(), new DownloadBatch(List.of(), Runnable::run));
    if (connectionId == null) {
      when(pluginsRepository.getEmbeddedPlugins()).thenReturn(configuration);
    } else {
      when(pluginsRepository.getPlugins(connectionId)).thenReturn(configuration);
    }
  }

  private PluginStatus status(@Nullable String connectionId, SonarLanguage language) {
    return underTest.getPluginStatuses(connectionId).stream().filter(status -> status.language() == language).findFirst().orElseThrow();
  }

  @Test
  void unloadPlugins_should_not_publish_event_when_no_plugins_were_loaded() {
    var connectionId = "connection1";

    underTest.unloadPlugins(connectionId);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void unloadPlugins_should_evict_connected_strategy_from_cache() {
    var connectionId = "connection1";

    underTest.unloadPlugins(connectionId);

    verify(connectedArtifactsLoadingStrategyFactory).evict(connectionId);
  }

  @Test
  void unloadEmbeddedPlugins_should_not_publish_event_when_no_embedded_plugins_were_loaded() {
    underTest.unloadPlugins(null);

    verify(eventPublisher, never()).publishEvent(any());
  }

  private void mockNoConnection(String connectionId) {
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionConfigurationStorage.getConnectionById(connectionId)).thenReturn(null);
  }

  private void mockConnection(String connectionId, ConnectionKind kind, Version version) {
    var connection = createConnection(connectionId, kind);
    mockConnection(connection);
    mockConnectionVersion(version);
  }

  private AbstractConnectionConfiguration createConnection(String connectionId, ConnectionKind kind) {
    var connection = mock(AbstractConnectionConfiguration.class);
    when(connection.getConnectionId()).thenReturn(connectionId);
    when(connection.getKind()).thenReturn(kind);
    return connection;
  }

  private void mockConnection(AbstractConnectionConfiguration connection) {
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(storageService.connection(connection.getConnectionId())).thenReturn(connectionStorage);
    when(connectionConfigurationStorage.getConnectionById(connection.getConnectionId())).thenReturn(connection);
  }

  private void mockPlugin(String pluginKey) {
    mockPlugin(pluginKey, null);
  }

  private void mockPlugin(String pluginKey, @Nullable Path jarPath) {
    var plugins = new HashMap<String, Path>();
    plugins.put(pluginKey, jarPath);
    mockPlugins(plugins);
  }

  private void mockPlugins(Map<String, Path> plugins) {
    var storedPlugins = new ArrayList<StoredPlugin>();
    var storedPluginsByKey = new HashMap<String, StoredPlugin>();
    plugins.forEach((pluginKey, jarPath) -> {
      var plugin = mock(StoredPlugin.class);
      when(plugin.getKey()).thenReturn(pluginKey);
      when(plugin.getJarPath()).thenReturn(jarPath);
      storedPlugins.add(plugin);
      storedPluginsByKey.put(pluginKey, plugin);
    });
    when(pluginStorage.getStoredPlugins()).thenReturn(storedPlugins);
    when(pluginStorage.getStoredPluginsByKey()).thenReturn(storedPluginsByKey);
  }

  private void mockConnectionVersion(Version version) {
    var serverInfo = mock(StoredServerInfo.class);
    when(serverInfo.version()).thenReturn(version);
    when(serverInfoStorage.read()).thenReturn(Optional.of(serverInfo));
  }

  private void mockEnabledLanguages(Language... languages) {
    when(initializeParams.getEnabledLanguagesInStandaloneMode()).thenReturn(Set.of(languages));
    when(initializeParams.getBackendCapabilities()).thenReturn(Set.of());
  }
}
