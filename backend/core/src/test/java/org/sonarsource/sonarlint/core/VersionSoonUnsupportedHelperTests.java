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
package org.sonarsource.sonarlint.core;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.LogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopesAddedEvent;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.SonarCloudConnectionConfiguration;
import org.sonarsource.sonarlint.core.repository.connection.SonarQubeConnectionConfiguration;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.ServerConnection;
import org.sonarsource.sonarlint.core.serverconnection.VersionUtils;
import org.sonarsource.sonarlint.core.sync.SynchronizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@Disabled("SLCORE-685 Some tests fail depending on the current date")
class VersionSoonUnsupportedHelperTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONFIG_SCOPE_ID_2 = "configScopeId2";
  private static final String SQ_CONNECTION_ID = "sqConnectionId";
  private static final String SQ_CONNECTION_ID_2 = "sqConnectionId2";
  private static final String SC_CONNECTION_ID = "scConnectionId";
  private static final SonarQubeConnectionConfiguration SQ_CONNECTION = new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID, "https://mysonarqube.com", true);
  private static final SonarQubeConnectionConfiguration SQ_CONNECTION_2 = new SonarQubeConnectionConfiguration(SQ_CONNECTION_ID_2, "https://mysonarqube2.com", true);
  private static final SonarCloudConnectionConfiguration SC_CONNECTION = new SonarCloudConnectionConfiguration(SonarCloudActiveEnvironment.PRODUCTION_URI, SC_CONNECTION_ID, "https://sonarcloud.com", true);

  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final ServerApiProvider serverApiProvider = mock(ServerApiProvider.class);
  private final SynchronizationService synchronizationService = mock(SynchronizationService.class);

  private ConfigurationRepository configRepository;
  private ConnectionConfigurationRepository connectionRepository;
  private VersionSoonUnsupportedHelper underTest;

  @BeforeEach
  void init() {
    configRepository = new ConfigurationRepository();
    connectionRepository = new ConnectionConfigurationRepository();
    underTest = new VersionSoonUnsupportedHelper(client, configRepository, serverApiProvider, connectionRepository, synchronizationService);
  }

  @Test
  void should_trigger_notification_when_new_binding_to_previous_lts_detected_on_config_scope_event() {
    var bindingConfiguration = new BindingConfiguration(SQ_CONNECTION_ID, "", true);
    configRepository.addOrReplace(ConfigurationScope.builder().setId(CONFIG_SCOPE_ID).setParentId(null).setBindable(false).setName("").setSetFocusOnNewCode(false).build(), bindingConfiguration);
    configRepository.addOrReplace(ConfigurationScope.builder().setId(CONFIG_SCOPE_ID_2).setParentId(null).setBindable(false).setName("").setSetFocusOnNewCode(false).build(), bindingConfiguration);
    connectionRepository.addOrReplace(SQ_CONNECTION);
    var serverApi = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(VersionUtils.getMinimalSupportedVersion());
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);

    underTest.configurationScopesAdded(new ConfigurationScopesAddedEvent(Set.of(CONFIG_SCOPE_ID, CONFIG_SCOPE_ID_2)));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsOnly("Connection '" + SQ_CONNECTION_ID + "' with version '" + VersionUtils.getMinimalSupportedVersion().getName() + "' is detected to be soon unsupported"));
  }

  @Test
  void should_trigger_multiple_notification_when_new_bindings_to_previous_lts_detected_on_config_scope_event() {
    var bindingConfiguration = new BindingConfiguration(SQ_CONNECTION_ID, "", true);
    var bindingConfiguration2 = new BindingConfiguration(SQ_CONNECTION_ID_2, "", true);
    configRepository.addOrReplace(ConfigurationScope.builder().setId(CONFIG_SCOPE_ID).setParentId(null).setBindable(false).setName("").setSetFocusOnNewCode(false).build(), bindingConfiguration);
    configRepository.addOrReplace(ConfigurationScope.builder().setId(CONFIG_SCOPE_ID_2).setParentId(null).setBindable(false).setName("").setSetFocusOnNewCode(false).build(), bindingConfiguration2);
    connectionRepository.addOrReplace(SQ_CONNECTION);
    connectionRepository.addOrReplace(SQ_CONNECTION_2);
    var serverApi = mock(ServerApi.class);
    var serverApi2 = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID_2)).thenReturn(Optional.of(serverApi2));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(VersionUtils.getMinimalSupportedVersion());
    var serverConnection2 = mock(ServerConnection.class);
    when(serverConnection2.readOrSynchronizeServerVersion(eq(serverApi2), any(SonarLintCancelMonitor.class))).thenReturn(Version.create(VersionUtils.getMinimalSupportedVersion() + ".9"));
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID_2, serverApi2)).thenReturn(serverConnection2);

    underTest.configurationScopesAdded(new ConfigurationScopesAddedEvent(Set.of(CONFIG_SCOPE_ID, CONFIG_SCOPE_ID_2)));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsOnly(
        "Connection '" + SQ_CONNECTION_ID + "' with version '" + VersionUtils.getMinimalSupportedVersion().getName() + "' is detected to be soon unsupported",
        "Connection '" + SQ_CONNECTION_ID_2 + "' with version '" + VersionUtils.getMinimalSupportedVersion() + ".9' is detected to be soon unsupported"
      ));
  }

  @Test
  void should_not_trigger_notification_when_config_scope_has_no_effective_binding() {
    underTest.configurationScopesAdded(new ConfigurationScopesAddedEvent(Set.of(CONFIG_SCOPE_ID)));

    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void should_trigger_notification_when_new_binding_to_previous_lts_detected() {
    connectionRepository.addOrReplace(SQ_CONNECTION);
    var serverApi = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(VersionUtils.getMinimalSupportedVersion());
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SQ_CONNECTION_ID, "", false)));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsOnly("Connection '" + SQ_CONNECTION_ID + "' with version '" + VersionUtils.getMinimalSupportedVersion().getName() + "' is detected to be soon unsupported"));
  }

  @Test
  void should_trigger_once_when_same_binding_to_previous_lts_detected_twice() {
    connectionRepository.addOrReplace(SQ_CONNECTION);
    var serverApi = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(VersionUtils.getMinimalSupportedVersion());
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SQ_CONNECTION_ID, "", false)));
    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SQ_CONNECTION_ID, "", false)));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsOnly("Connection '" + SQ_CONNECTION_ID + "' with version '" + VersionUtils.getMinimalSupportedVersion().getName() + "' is detected to be soon unsupported"));
  }

  @Test
  void should_trigger_notification_when_new_binding_to_in_between_lts_detected() {
    connectionRepository.addOrReplace(SQ_CONNECTION);
    var serverApi = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(Version.create(VersionUtils.getMinimalSupportedVersion().getName() + ".9"));
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SQ_CONNECTION_ID, "", false)));

    await().untilAsserted(() -> assertThat(logTester.logs(LogOutput.Level.DEBUG))
      .containsOnly("Connection '" + SQ_CONNECTION_ID + "' with version '" + VersionUtils.getMinimalSupportedVersion().getName() + ".9' is detected to be soon unsupported"));
  }

  @Test
  void should_not_trigger_notification_when_new_binding_to_current_lts_detected() {
    connectionRepository.addOrReplace(SQ_CONNECTION);
    var serverApi = mock(ServerApi.class);
    when(serverApiProvider.getServerApi(SQ_CONNECTION_ID)).thenReturn(Optional.of(serverApi));
    var serverConnection = mock(ServerConnection.class);
    when(serverConnection.readOrSynchronizeServerVersion(eq(serverApi), any(SonarLintCancelMonitor.class))).thenReturn(VersionUtils.getCurrentLts());
    when(synchronizationService.getServerConnection(SQ_CONNECTION_ID, serverApi)).thenReturn(serverConnection);

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SQ_CONNECTION_ID, "", false)));

    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void should_not_trigger_notification_when_sonarcloud_binding_detected() {
    connectionRepository.addOrReplace(SC_CONNECTION);

    underTest.bindingConfigChanged(new BindingConfigChangedEvent(CONFIG_SCOPE_ID, null,
      new BindingConfiguration(SC_CONNECTION_ID, "", false)));

    assertThat(logTester.logs()).isEmpty();
  }

}
