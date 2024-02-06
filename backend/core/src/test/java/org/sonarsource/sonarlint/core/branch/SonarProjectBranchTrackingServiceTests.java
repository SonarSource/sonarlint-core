/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.branch;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.branch.MatchSonarProjectBranchResponse;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranches;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBranchesStorage;
import org.sonarsource.sonarlint.core.serverconnection.SonarProjectStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SonarProjectBranchTrackingServiceTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester(true);

  public static final String CONNECTION_ID = "connectionId";
  public static final String PROJECT_KEY = "projectKey";
  public static final String CONFIG_SCOPE_ID = "configScopeId";
  private SonarProjectBranchTrackingService underTest;
  private final SonarLintRpcClient sonarLintRpcClient = mock(SonarLintRpcClient.class);
  private final StorageService storageService = mock(StorageService.class);
  private final ConfigurationRepository configurationRepository = mock(ConfigurationRepository.class);
  private ProjectBranchesStorage projectBranchesStorage;

  @BeforeEach
  void prepare() {
    when(configurationRepository.getConfigurationScope(CONFIG_SCOPE_ID)).thenReturn(new ConfigurationScope(CONFIG_SCOPE_ID, null, true, "Test config scope"));
    var binding = new Binding(CONNECTION_ID, PROJECT_KEY);
    when(configurationRepository.getEffectiveBinding(CONFIG_SCOPE_ID)).thenReturn(Optional.of(binding));
    var sonarProjectStorage = mock(SonarProjectStorage.class);
    when(storageService.binding(binding)).thenReturn(sonarProjectStorage);
    projectBranchesStorage = mock(ProjectBranchesStorage.class);
    when(sonarProjectStorage.branches()).thenReturn(projectBranchesStorage);
    underTest = new SonarProjectBranchTrackingService(sonarLintRpcClient, storageService, configurationRepository, mock(ApplicationEventPublisher.class));
  }

  @AfterEach
  void shutdown() {
    underTest.shutdown();
  }

  @Test
  void shouldCancelPreviousJobIfNewOneIsSubmitted() {
    when(projectBranchesStorage.exists()).thenReturn(true);
    when(projectBranchesStorage.read()).thenReturn(new ProjectBranches(Set.of("main", "feature"), "main"));

    var firstFuture = new CompletableFuture<MatchSonarProjectBranchResponse>();
    when(sonarLintRpcClient.matchSonarProjectBranch(any()))
      // Emulate a long response for the first request
      .thenReturn(firstFuture)
      .thenReturn(CompletableFuture.completedFuture(new MatchSonarProjectBranchResponse("feature")));

    // This should queue a first branch matching
    underTest.onConfigurationScopesAdded(new ConfigurationScopesAddedEvent(Set.of(CONFIG_SCOPE_ID)));
    // Wait for the RPC client to be called
    verify(sonarLintRpcClient, timeout(1000)).matchSonarProjectBranch(any());

    // This should cancel the previous branch matching, and queue a new one
    underTest.didVcsRepositoryChange(CONFIG_SCOPE_ID);

    assertThat(underTest.awaitEffectiveSonarProjectBranch(CONFIG_SCOPE_ID)).contains("feature");

    assertThat(firstFuture).isCancelled();

    verify(sonarLintRpcClient, timeout(1000).times(1)).didChangeMatchedSonarProjectBranch(any());
  }

  @Test
  void shouldUnlockThoseAwaitingForBranchOnErrorAndDefaultToMain() {
    when(projectBranchesStorage.exists()).thenReturn(true);
    when(projectBranchesStorage.read()).thenReturn(new ProjectBranches(Set.of("main", "feature"), "main"));

    var rpcFuture = new CompletableFuture<MatchSonarProjectBranchResponse>();
    when(sonarLintRpcClient.matchSonarProjectBranch(any()))
      .thenReturn(rpcFuture);

    // This should queue a first branch matching
    underTest.onConfigurationScopesAdded(new ConfigurationScopesAddedEvent(Set.of(CONFIG_SCOPE_ID)));
    // Wait for the RPC client to be called
    verify(sonarLintRpcClient, timeout(1000)).matchSonarProjectBranch(any());

    rpcFuture.completeExceptionally(new RuntimeException("Unexpected error"));

    assertThat(underTest.awaitEffectiveSonarProjectBranch(CONFIG_SCOPE_ID)).contains("main");

    await().untilAsserted(() -> assertThat(logTester.getSlf4jLogs()).extracting(ILoggingEvent::getFormattedMessage)
      .contains("Matched Sonar project branch for configuration scope 'configScopeId' changed from 'null' to 'main'"));
  }

}
