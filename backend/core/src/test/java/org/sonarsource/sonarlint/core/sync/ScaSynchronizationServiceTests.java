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
package org.sonarsource.sonarlint.core.sync;

import io.sentry.Sentry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.DependencyRisksSynchronizedEvent;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.sca.GetIssuesReleasesResponse;
import org.sonarsource.sonarlint.core.serverapi.sca.ScaApi;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScaSynchronizationServiceTests {

  private MockedStatic<Sentry> sentry;

  @AfterEach
  void closeSentry() {
    sentry.close();
  }

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String BRANCH_NAME = "main";

  private ScaSynchronizationService underTest;
  private ScaApi scaApi;
  private ProjectServerIssueStore findingsStore;
  private ApplicationEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    var logger = Sentry.logger();
    sentry = mockStatic(Sentry.class);
    sentry.when(Sentry::logger).thenReturn(logger);

    var initializeParams = mock(InitializeParams.class);
    when(initializeParams.getBackendCapabilities()).thenReturn(EnumSet.of(BackendCapability.SCA_SYNCHRONIZATION));

    var storageService = mock(StorageService.class);
    var connectionStorage = mock(ConnectionStorage.class);
    var projectStorage = mock(SonarProjectStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    findingsStore = mock(ProjectServerIssueStore.class);

    when(storageService.connection(CONNECTION_ID)).thenReturn(connectionStorage);
    when(connectionStorage.project(PROJECT_KEY)).thenReturn(projectStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(projectStorage.findings()).thenReturn(findingsStore);
    when(findingsStore.loadDependencyRisks(BRANCH_NAME)).thenReturn(List.of());

    var storedServerInfo = mock(StoredServerInfo.class);
    when(storedServerInfo.hasFeature(Feature.SCA)).thenReturn(true);
    when(serverInfoStorage.read()).thenReturn(Optional.of(storedServerInfo));

    scaApi = mock(ScaApi.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    underTest = new ScaSynchronizationService(storageService, eventPublisher, initializeParams);
  }

  @ParameterizedTest
  @ValueSource(strings = {"item", "key", "type", "severity", "quality", "status", "release", "release.packageName", "release.version", "transitions", "transition"})
  void should_skip_report_and_replace_around_malformed_risk(String field) {
    var first = issueRelease(UUID.randomUUID(), GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY, List.of());
    var last = issueRelease(UUID.randomUUID(), GetIssuesReleasesResponse.IssuesRelease.Type.MALWARE, List.of());
    var malformed = new GetIssuesReleasesResponse.IssuesRelease(
      "key".equals(field) ? null : UUID.randomUUID(),
      "type".equals(field) ? null : first.type(),
      "severity".equals(field) ? null : first.severity(),
      "quality".equals(field) ? null : first.quality(),
      "status".equals(field) ? null : first.status(),
      "release".equals(field) ? null : new GetIssuesReleasesResponse.IssuesRelease.Release(
        "release.packageName".equals(field) ? null : "pkg", "release.version".equals(field) ? null : "1"),
      null, null, "transitions".equals(field) ? null : "transition".equals(field) ? Arrays.asList((GetIssuesReleasesResponse.IssuesRelease.Transition) null) : List.of());
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(Arrays.asList(first, "item".equals(field) ? null : malformed, last), new GetIssuesReleasesResponse.Page(3)));

    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());

    var saved = ArgumentCaptor.forClass(List.class);
    verify(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), saved.capture());
    assertThat((List<ServerDependencyRisk>) saved.getValue()).extracting(ServerDependencyRisk::key).containsExactly(first.key(), last.key());
    var error = ArgumentCaptor.forClass(Throwable.class);
    sentry.verify(() -> Sentry.captureException(error.capture()));
    assertThat(error.getValue()).isInstanceOf(InvalidServerItemException.class).hasMessageContaining(field);
    var rejectedKey = "item".equals(field) ? null : malformed.key();
    assertThat(logTester.logs()).contains("[SYNC] Skipping dependency risk '" + rejectedKey + "': Missing or unknown dependency risk " + field);
    verify(eventPublisher).publishEvent(any(DependencyRisksSynchronizedEvent.class));
  }

  @Test
  void should_keep_nullable_fields_and_empty_transitions() {
    var risk = new GetIssuesReleasesResponse.IssuesRelease(UUID.randomUUID(), GetIssuesReleasesResponse.IssuesRelease.Type.PROHIBITED_LICENSE,
      GetIssuesReleasesResponse.IssuesRelease.Severity.INFO, GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.MAINTAINABILITY,
      GetIssuesReleasesResponse.IssuesRelease.Status.ACCEPT, new GetIssuesReleasesResponse.IssuesRelease.Release("", ""), null, null, List.of());
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(List.of(risk), new GetIssuesReleasesResponse.Page(1)));

    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());

    var saved = ArgumentCaptor.forClass(List.class);
    verify(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), saved.capture());
    assertThat((List<ServerDependencyRisk>) saved.getValue()).singleElement().satisfies(value -> {
      assertThat(value.vulnerabilityId()).isNull();
      assertThat(value.cvssScore()).isNull();
      assertThat(value.transitions()).isEmpty();
      assertThat(value.packageName()).isEmpty();
      assertThat(value.packageVersion()).isEmpty();
    });
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"load", "replace", "transport", "cancellation", "unexpected"})
  void should_propagate_operational_failures_without_reporting(String boundary) {
    RuntimeException failure = "cancellation".equals(boundary) ? new CancellationException() : new IllegalStateException("operational failure");
    var response = new GetIssuesReleasesResponse(List.of(), new GetIssuesReleasesResponse.Page(0));
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any())).thenReturn(response);
    switch (boundary) {
      case "load" -> when(findingsStore.loadDependencyRisks(BRANCH_NAME)).thenThrow(failure);
      case "replace" -> doThrow(failure).when(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), any());
      default -> when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any())).thenThrow(failure);
    }

    assertThatThrownBy(() -> underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor()))
      .isSameAs(failure);
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
    verify(eventPublisher, never()).publishEvent(any(DependencyRisksSynchronizedEvent.class));
  }

  @Test
  void should_not_publish_event_when_no_risks_changed() {
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(List.of(), new GetIssuesReleasesResponse.Page(0)));
    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());
    verify(findingsStore).replaceAllDependencyRisksOfBranch(BRANCH_NAME, List.of());
    verify(eventPublisher, never()).publishEvent(any(DependencyRisksSynchronizedEvent.class));
  }

  @Test
  void should_sync_malware_dependency_risk() {
    var key = UUID.randomUUID();
    var issueRelease = issueRelease(key, GetIssuesReleasesResponse.IssuesRelease.Type.MALWARE,
      List.of(GetIssuesReleasesResponse.IssuesRelease.Transition.CONFIRM));
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(List.of(issueRelease), new GetIssuesReleasesResponse.Page(1)));

    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());

    var captor = ArgumentCaptor.forClass(List.class);
    verify(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), captor.capture());
    List<ServerDependencyRisk> saved = captor.getValue();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).key()).isEqualTo(key);
    assertThat(saved.get(0).type()).isEqualTo(ServerDependencyRisk.Type.MALWARE);
  }

  @Test
  void should_skip_dependency_risk_with_unknown_type() {
    var key = UUID.randomUUID();
    var issueRelease = issueRelease(key, null /* unknown type — Gson maps unknown enum values to null */, List.of());
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(List.of(issueRelease), new GetIssuesReleasesResponse.Page(1)));

    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());

    var captor = ArgumentCaptor.forClass(List.class);
    verify(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), captor.capture());
    assertThat(captor.getValue()).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Skipping dependency risk"));
  }

  @Test
  void should_skip_dependency_risk_with_unknown_transition() {
    var key = UUID.randomUUID();
    // null in transitions list — Gson maps unknown enum values in collections to null
    var transitions = new ArrayList<GetIssuesReleasesResponse.IssuesRelease.Transition>();
    transitions.add(null);
    var issueRelease = issueRelease(key, GetIssuesReleasesResponse.IssuesRelease.Type.VULNERABILITY, transitions);
    when(scaApi.getIssuesReleases(eq(PROJECT_KEY), eq(BRANCH_NAME), any()))
      .thenReturn(new GetIssuesReleasesResponse(List.of(issueRelease), new GetIssuesReleasesResponse.Page(1)));

    underTest.synchronize(serverApiWith(scaApi), CONNECTION_ID, PROJECT_KEY, BRANCH_NAME, new SonarLintCancelMonitor());

    var captor = ArgumentCaptor.forClass(List.class);
    verify(findingsStore).replaceAllDependencyRisksOfBranch(eq(BRANCH_NAME), captor.capture());
    assertThat(captor.getValue()).isEmpty();
    assertThat(logTester.logs()).anyMatch(log -> log.contains("Skipping dependency risk"));
  }

  private static GetIssuesReleasesResponse.IssuesRelease issueRelease(
    UUID key,
    GetIssuesReleasesResponse.IssuesRelease.Type type,
    List<GetIssuesReleasesResponse.IssuesRelease.Transition> transitions) {
    var release = new GetIssuesReleasesResponse.IssuesRelease.Release("com.example:pkg", "1.0.0");
    return new GetIssuesReleasesResponse.IssuesRelease(
      key, type,
      GetIssuesReleasesResponse.IssuesRelease.Severity.HIGH,
      GetIssuesReleasesResponse.IssuesRelease.SoftwareQuality.SECURITY,
      GetIssuesReleasesResponse.IssuesRelease.Status.OPEN,
      release, null, null, transitions);
  }

  private static ServerApi serverApiWith(ScaApi scaApi) {
    var serverApi = mock(ServerApi.class);
    when(serverApi.sca()).thenReturn(scaApi);
    return serverApi;
  }
}
