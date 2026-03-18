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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.repository.config.BindingConfiguration;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationScope;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidChangePluginStatusesParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginStatusNotifierServiceTest {

  private static final String SCOPE_1 = "scope1";
  private static final String SCOPE_2 = "scope2";
  private static final String CONNECTION_1 = "conn1";

  private final SonarLintRpcClient client = mock(SonarLintRpcClient.class);
  private final PluginsService pluginsService = mock(PluginsService.class);
  private final ConfigurationRepository configurationRepository = new ConfigurationRepository();
  private final PluginStatusNotifierService underTest = new PluginStatusNotifierService(pluginsService, client, configurationRepository);

  private final PluginStatus standaloneStatus = new PluginStatus(SonarLanguage.JAVA, PluginState.ACTIVE, ArtifactSource.EMBEDDED, null, null, null);
  private final PluginStatus connectedStatus = new PluginStatus(SonarLanguage.JAVA, PluginState.ACTIVE, ArtifactSource.SONARQUBE_SERVER, null, null, "10.0.0");

  @BeforeEach
  void setUp() {
    when(pluginsService.getPluginStatuses(null)).thenReturn(List.of(standaloneStatus));
    when(pluginsService.getPluginStatuses(CONNECTION_1)).thenReturn(List.of(connectedStatus));
  }

  @Test
  void should_notify_each_scope_with_its_effective_connection_statuses_in_standalone_mode() {
    configurationRepository.addOrReplace(new ConfigurationScope(SCOPE_1, null, true, "Scope 1"), BindingConfiguration.noBinding());
    configurationRepository.addOrReplace(new ConfigurationScope(SCOPE_2, null, true, "Scope 2"), new BindingConfiguration(CONNECTION_1, "project1", false));

    var expectedScope1Params = new DidChangePluginStatusesParams(SCOPE_1, List.of(standaloneStatusDto()));
    var expectedScope2Params = new DidChangePluginStatusesParams(SCOPE_2, List.of(connectedStatusDto()));

    underTest.onPluginStatusesChanged(new PluginStatusesChangedEvent(null));

    var captor = ArgumentCaptor.forClass(DidChangePluginStatusesParams.class);
    verify(client, times(2)).didChangePluginStatuses(captor.capture());
    assertThat(captor.getAllValues())
      .usingRecursiveComparison()
      .ignoringCollectionOrder()
      .isEqualTo(List.of(expectedScope1Params, expectedScope2Params));
  }

  @Test
  void should_notify_only_bound_scopes_in_connected_mode() {
    configurationRepository.addOrReplace(new ConfigurationScope(SCOPE_1, null, true, "Scope 1"), new BindingConfiguration(CONNECTION_1, "project1", false));
    configurationRepository.addOrReplace(new ConfigurationScope(SCOPE_2, null, true, "Scope 2"), new BindingConfiguration(CONNECTION_1, "project2", false));
    configurationRepository.addOrReplace(new ConfigurationScope("scope3", null, true, "Scope 3"), BindingConfiguration.noBinding());

    var expectedScope1Params = new DidChangePluginStatusesParams(SCOPE_1, List.of(connectedStatusDto()));
    var expectedScope2Params = new DidChangePluginStatusesParams(SCOPE_2, List.of(connectedStatusDto()));

    underTest.onPluginStatusesChanged(new PluginStatusesChangedEvent(CONNECTION_1));

    var captor = ArgumentCaptor.forClass(DidChangePluginStatusesParams.class);
    verify(client, times(2)).didChangePluginStatuses(captor.capture());
    assertThat(captor.getAllValues())
      .usingRecursiveComparison()
      .ignoringCollectionOrder()
      .isEqualTo(List.of(expectedScope1Params, expectedScope2Params));
  }

  private static PluginStatusDto standaloneStatusDto() {
    return new PluginStatusDto(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, null, null, null);
  }

  private static PluginStatusDto connectedStatusDto() {
    return new PluginStatusDto(org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.SONARQUBE_SERVER, null, null, "10.0.0");
  }

}
