/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ConnectionStorage;
import org.sonarsource.sonarlint.core.serverconnection.ServerSettings;
import org.sonarsource.sonarlint.core.serverconnection.StoredServerInfo;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.branch.SonarProjectBranchTrackingService;
import org.sonarsource.sonarlint.core.sync.ScaSynchronizationService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyRiskServiceTests {

  private ConfigurationRepository configurationRepository;
  private ConnectionConfigurationRepository connectionRepository;
  private StorageService storageService;
  private SonarQubeClientManager sonarQubeClientManager;
  private DependencyRiskService underTest;

  @BeforeEach
  void setUp() {
    configurationRepository = new ConfigurationRepository();
    connectionRepository = mock(ConnectionConfigurationRepository.class);
    storageService = mock(StorageService.class);
    sonarQubeClientManager = mock(SonarQubeClientManager.class);
    var branchTrackingService = mock(SonarProjectBranchTrackingService.class);
    var scaSynchronizationService = mock(ScaSynchronizationService.class);
    var client = mock(SonarLintRpcClient.class);
    var telemetryService = mock(TelemetryService.class);

    underTest = new DependencyRiskService(configurationRepository, connectionRepository, storageService, sonarQubeClientManager,
      branchTrackingService, scaSynchronizationService, client, telemetryService);
  }

  @Test
  void testBuildSonarQubeServerScaUrl() {
    var dependencyKey = UUID.randomUUID();

    var dependencyRiskUrl = DependencyRiskService.buildDependencyRiskBrowseUrl("myProject", "myBranch", dependencyKey, new EndpointParams("http://foo.com", "", false, null));

    assertThat(dependencyRiskUrl).isEqualTo(String.format("http://foo.com/dependency-risks/%s/what?id=myProject&branch=myBranch", dependencyKey));
  }

  @Test
  void checkSupported_should_fail_when_scope_not_found() {
    assertThatThrownBy(() -> underTest.checkSupported("unknown"))
      .isInstanceOf(ResponseErrorException.class)
      .extracting("responseError")
      .extracting("message")
      .asString()
      .contains("does not exist");
  }

  @Test
  void checkSupported_should_fail_when_not_bound() {
    var scopeId = "scope";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "name"), BindingConfiguration.noBinding(false));

    var resp = underTest.checkSupported(scopeId);

    assertThat(resp.isSupported()).isFalse();
    assertThat(resp.getReason()).contains("2025.4");
  }

  @Test
  void checkSupported_should_fail_when_connection_missing() {
    var scopeId = "scope";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "name"),
      new BindingConfiguration("conn", "proj", false));
    when(connectionRepository.getConnectionById("conn")).thenReturn(null);

    assertThatThrownBy(() -> underTest.checkSupported(scopeId))
      .isInstanceOf(ResponseErrorException.class)
      .extracting("responseError")
      .extracting("message")
      .asString()
      .contains("unknown connection");
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkSupported_should_fail_on_sonarcloud() {
    var scopeId = "scope";
    var connectionId = "c1";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "n"),
      new BindingConfiguration(connectionId, "proj", false));
    var serverApi = mock(ServerApi.class);
    when(serverApi.isSonarCloud()).thenReturn(true);
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any())).thenAnswer(inv -> Optional.ofNullable(((Function<ServerApi, Object>) inv.getArgument(1)).apply(serverApi)));
    when(connectionRepository.getConnectionById(connectionId)).thenReturn(new SonarQubeConnectionConfiguration(connectionId, "url", true));

    var resp = underTest.checkSupported(scopeId);

    assertThat(resp.isSupported()).isFalse();
    assertThat(resp.getReason()).contains("does not yet support");
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkSupported_should_fail_on_old_sq() {
    var scopeId = "scope";
    var connectionId = "c1";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "n"),
      new BindingConfiguration(connectionId, "proj", false));
    var serverApi = mock(ServerApi.class);
    when(serverApi.isSonarCloud()).thenReturn(false);
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any())).thenAnswer(inv -> Optional.ofNullable(((Function<ServerApi, Object>) inv.getArgument(1)).apply(serverApi)));
    when(connectionRepository.getConnectionById(connectionId)).thenReturn(new SonarQubeConnectionConfiguration(connectionId, "url", true));
    var connectionStorage = mock(ConnectionStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.of(new StoredServerInfo(Version.create("2025.3"), new ServerSettings(Map.of()))));

    var respOld = underTest.checkSupported(scopeId);

    assertThat(respOld.isSupported()).isFalse();
    assertThat(respOld.getReason()).contains("lower than the minimum supported version 2025.4");
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkSupported_should_fail_on_sca_disabled() {
    var scopeId = "scope";
    var connectionId = "c1";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "n"),
      new BindingConfiguration(connectionId, "proj", false));
    var serverApi = mock(ServerApi.class);
    when(serverApi.isSonarCloud()).thenReturn(false);
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any())).thenAnswer(inv -> Optional.ofNullable(((Function<ServerApi, Object>) inv.getArgument(1)).apply(serverApi)));
    when(connectionRepository.getConnectionById(connectionId)).thenReturn(new SonarQubeConnectionConfiguration(connectionId, "url", true));
    var connectionStorage = mock(ConnectionStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.of(new StoredServerInfo(Version.create("2025.4"), new ServerSettings(Map.of(ServerSettings.SCA_ENABLED, "false")))));

    var respNoSca = underTest.checkSupported(scopeId);

    assertThat(respNoSca.isSupported()).isFalse();
    assertThat(respNoSca.getReason()).contains("does not have Advanced Security enabled");
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkSupported_should_pass_when_sq_new_enough_and_sca_enabled() {
    var scopeId = "scope";
    var connectionId = "c1";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "n"),
      new BindingConfiguration(connectionId, "proj", false));
    var serverApi = mock(ServerApi.class);
    when(serverApi.isSonarCloud()).thenReturn(false);
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any())).thenAnswer(inv -> Optional.ofNullable(((Function<ServerApi, Object>) inv.getArgument(1)).apply(serverApi)));
    when(connectionRepository.getConnectionById(connectionId)).thenReturn(new SonarQubeConnectionConfiguration(connectionId, "url", true));
    var connectionStorage = mock(ConnectionStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.of(new StoredServerInfo(Version.create("2025.4"), new ServerSettings(Map.of(ServerSettings.SCA_ENABLED, "true")))));

    var resp = underTest.checkSupported(scopeId);

    assertThat(resp.isSupported()).isTrue();
    assertThat(resp.getReason()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void checkSupported_should_fail_when_server_info_missing() {
    var scopeId = "scope";
    var connectionId = "c1";
    configurationRepository.addOrReplace(new ConfigurationScope(scopeId, null, true, "n"),
      new BindingConfiguration(connectionId, "proj", false));
    var serverApi = mock(ServerApi.class);
    when(serverApi.isSonarCloud()).thenReturn(false);
    when(sonarQubeClientManager.withActiveClientAndReturn(eq(connectionId), any())).thenAnswer(inv -> Optional.ofNullable(((Function<ServerApi, Object>) inv.getArgument(1)).apply(serverApi)));
    when(connectionRepository.getConnectionById(connectionId)).thenReturn(new SonarQubeConnectionConfiguration(connectionId, "url", true));
    var connectionStorage = mock(ConnectionStorage.class);
    var serverInfoStorage = mock(ServerInfoStorage.class);
    when(storageService.connection(connectionId)).thenReturn(connectionStorage);
    when(connectionStorage.serverInfo()).thenReturn(serverInfoStorage);
    when(serverInfoStorage.read()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> underTest.checkSupported(scopeId))
      .isInstanceOf(ResponseErrorException.class)
      .extracting("responseError")
      .extracting("message")
      .asString()
      .contains("Could not retrieve server information");
  }

}
