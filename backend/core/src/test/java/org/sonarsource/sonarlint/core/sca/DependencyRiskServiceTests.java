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
package org.sonarsource.sonarlint.core.sca;

import com.sonar.sca.scanner.analyzeproject.response.AnalysisErrorResource;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectIssue;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectRelease;
import com.sonar.sca.scanner.analyzeproject.response.AnalyzeProjectResponse;
import com.sonar.sca.scanner.analyzeproject.response.Cwe;
import com.sonar.sca.scanner.analyzeproject.response.ScaIssueType;
import com.sonar.sca.scanner.analyzeproject.response.SoftwareQuality;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeRemovedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.sca.DidChangeDependencyRisksParams;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectServerIssueStore;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.sync.ScaSynchronizationService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyRiskServiceTests {

  @RegisterExtension
  static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";
  private static final String PROJECT_KEY = "projectKey";
  private static final String BRANCH_NAME = "main";

  private final ConfigurationRepository configurationRepository = new ConfigurationRepository();
  private final ConnectionConfigurationRepository connectionRepository = new ConnectionConfigurationRepository();
  private final StorageService storageService = mock(StorageService.class);
  private final SonarQubeClientManager sonarQubeClientManager = mock(SonarQubeClientManager.class);
  private final SonarProjectBranchTrackingService branchTrackingService = mock(SonarProjectBranchTrackingService.class);
  private final ScaSynchronizationService scaSynchronizationService = mock(ScaSynchronizationService.class);
  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final TelemetryService telemetryService = mock(TelemetryService.class);
  private final LocalDependencyRiskAnalysisCache localDependencyRiskAnalysisCache = new LocalDependencyRiskAnalysisCache();
  private final DependencyRiskMerger dependencyRiskMerger = new DependencyRiskMerger(new DependencyRiskDtoMapper());
  private final SonarProjectStorage projectStorage = mock(SonarProjectStorage.class);
  private final ProjectServerIssueStore findingsStore = mock(ProjectServerIssueStore.class);

  @Test
  void testBuildSonarQubeServerScaUrl() {
    var dependencyKey = UUID.randomUUID();
    assertThat(DependencyRiskService.buildDependencyRiskBrowseUrl("myProject", "myBranch", dependencyKey, new EndpointParams("http://foo.com", "", false, null)))
      .isEqualTo(String.format("http://foo.com/dependency-risks/%s/what?id=myProject&branch=myBranch", dependencyKey));
  }

  @Test
  void listAll_should_return_server_risks_merged_with_local_analysis_cache() {
    setupBoundScope();
    var sharedId = UUID.randomUUID();
    var serverRisk = sampleServerRisk(sharedId, "org.example:library", "1.2.3");
    setupServerRisks(List.of(serverRisk));
    localDependencyRiskAnalysisCache.put(CONFIG_SCOPE_ID, singleIssueResponse(sharedId.toString(), "org.example:library", "1.2.3"));

    var risks = underTest().listAll(CONFIG_SCOPE_ID, false, new SonarLintCancelMonitor());

    assertThat(risks).hasSize(1);
    var merged = risks.get(0);
    assertThat(merged.getId()).isEqualTo(sharedId);
    assertThat(merged.getPresence()).isEqualTo(DependencyRiskDto.Presence.SERVER_AND_LOCAL);
    assertThat(merged.getLocalAnalysisDetails()).isNotNull();
    assertThat(merged.getLocalAnalysisDetails().getDependencyDetails().getDependencyFilePaths()).containsExactly("pom.xml");
  }

  @Test
  void updateLocalAnalysisAndNotify_should_update_cache_and_notify_added_local_only_and_updated_matched_risks() {
    setupBoundScope();
    var sharedId = UUID.randomUUID();
    var serverRisk = sampleServerRisk(sharedId, "org.example:library", "1.2.3");
    setupServerRisks(List.of(serverRisk));
    var localAnalysis = new AnalyzeProjectResponse(List.of(
      releaseWithIssue(sharedId.toString(), "org.example:library", "1.2.3"),
      releaseWithIssue(null, "org.example:local-only", "4.5.6")), List.of("pom.xml"), List.of());

    var risks = underTest().updateLocalAnalysisAndNotify(CONFIG_SCOPE_ID, localAnalysis, new SonarLintCancelMonitor());

    assertThat(risks).hasSize(2);
    var paramsCaptor = ArgumentCaptor.forClass(DidChangeDependencyRisksParams.class);
    verify(client).didChangeDependencyRisks(paramsCaptor.capture());
    var params = paramsCaptor.getValue();
    assertThat(params.getConfigurationScopeId()).isEqualTo(CONFIG_SCOPE_ID);
    assertThat(params.getClosedDependencyRiskIds()).isEmpty();
    assertThat(params.getUpdatedDependencyRisks())
      .singleElement()
      .satisfies(risk -> {
        assertThat(risk.getId()).isEqualTo(sharedId);
        assertThat(risk.getPresence()).isEqualTo(DependencyRiskDto.Presence.SERVER_AND_LOCAL);
      });
    assertThat(params.getAddedDependencyRisks())
      .singleElement()
      .satisfies(risk -> {
        assertThat(risk.getId()).isNull();
        assertThat(risk.getPresence()).isEqualTo(DependencyRiskDto.Presence.LOCAL_ONLY);
        assertThat(risk.getPackageName()).isEqualTo("org.example:local-only");
      });
  }

  @Test
  void onConfigurationScopeRemoved_should_evict_local_analysis_cache_for_removed_scope_only() {
    var otherConfigurationScopeId = "otherConfigScopeId";
    var removedAnalysis = singleIssueResponse(UUID.randomUUID().toString(), "org.example:removed", "1.2.3");
    var otherAnalysis = singleIssueResponse(UUID.randomUUID().toString(), "org.example:other", "4.5.6");
    localDependencyRiskAnalysisCache.put(CONFIG_SCOPE_ID, removedAnalysis);
    localDependencyRiskAnalysisCache.put(otherConfigurationScopeId, otherAnalysis);

    underTest().onConfigurationScopeRemoved(new ConfigurationScopeRemovedEvent(new ConfigurationScope(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID), BindingConfiguration.noBinding()));

    assertThat(localDependencyRiskAnalysisCache.get(CONFIG_SCOPE_ID)).isEmpty();
    assertThat(localDependencyRiskAnalysisCache.get(otherConfigurationScopeId)).containsSame(otherAnalysis);
  }

  private DependencyRiskService underTest() {
    return new DependencyRiskService(configurationRepository, connectionRepository, storageService, sonarQubeClientManager, branchTrackingService,
      scaSynchronizationService, client, telemetryService, localDependencyRiskAnalysisCache, dependencyRiskMerger);
  }

  private void setupBoundScope() {
    configurationRepository.addOrReplace(new ConfigurationScope(CONFIG_SCOPE_ID, null, true, CONFIG_SCOPE_ID), new BindingConfiguration(CONNECTION_ID, PROJECT_KEY, false));
    when(branchTrackingService.awaitEffectiveSonarProjectBranch(CONFIG_SCOPE_ID)).thenReturn(Optional.of(BRANCH_NAME));
    when(storageService.binding(any())).thenReturn(projectStorage);
    when(projectStorage.findings()).thenReturn(findingsStore);
  }

  private void setupServerRisks(List<ServerDependencyRisk> serverRisks) {
    when(findingsStore.loadDependencyRisks(BRANCH_NAME)).thenReturn(serverRisks);
  }

  private static AnalyzeProjectResponse singleIssueResponse(String issueKey, String packageName, String version) {
    return new AnalyzeProjectResponse(List.of(releaseWithIssue(issueKey, packageName, version)), List.of("pom.xml"), List.<AnalysisErrorResource>of());
  }

  private static AnalyzeProjectRelease releaseWithIssue(String issueKey, String packageName, String version) {
    var issue = new AnalyzeProjectIssue(issueKey, "HIGH", true, ScaIssueType.VULNERABILITY, SoftwareQuality.SECURITY, "OPEN", "CVE-1234",
      List.of(new Cwe("CWE-79", "Improper Neutralization of Input During Web Page Generation", "Cross-site scripting")),
      "9.8", "MIT", List.of());
    return new AnalyzeProjectRelease("release-key", "pkg:maven/org.example/library@" + version, "maven", packageName, version, "MIT", true, true,
      false, List.of(issue), List.of("pom.xml"), List.of(List.of("root", packageName)));
  }

  private static ServerDependencyRisk sampleServerRisk(UUID id, String packageName, String packageVersion) {
    return new ServerDependencyRisk(id, ServerDependencyRisk.Type.VULNERABILITY, ServerDependencyRisk.Severity.HIGH, ServerDependencyRisk.SoftwareQuality.SECURITY,
      ServerDependencyRisk.Status.OPEN, packageName, packageVersion, "CVE-1234", "9.8", List.of(ServerDependencyRisk.Transition.CONFIRM));
  }

}
