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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScaSynchronizationServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String BRANCH_NAME = "main";

  private ScaSynchronizationService underTest;
  private ScaApi scaApi;
  private ProjectServerIssueStore findingsStore;

  @BeforeEach
  void setUp() {
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
    underTest = new ScaSynchronizationService(storageService, mock(ApplicationEventPublisher.class), initializeParams);
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
