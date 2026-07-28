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
package org.sonarsource.sonarlint.core.plugin.skipped;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.analysis.AnalysisFinishedEvent;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.plugin.commons.api.SkipReason;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.plugin.DidSkipLoadingPluginParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkippedPluginsNotifierServiceTest {

  @Test
  void should_notify_for_apex_when_the_dre_fallback_was_skipped() {
    var connectionId = "connectionId";
    var configurationScopeId = "configurationScopeId";
    var skippedPluginsRepository = new SkippedPluginsRepository();
    skippedPluginsRepository.setSkippedPlugins(connectionId, List.of(new SkippedPlugin("dre",
      new SkipReason.UnsatisfiedRuntimeRequirement(SkipReason.UnsatisfiedRuntimeRequirement.RuntimeRequirement.JRE, "17", "21"))));
    var configurationRepository = mock(ConfigurationRepository.class);
    var binding = mock(Binding.class);
    when(binding.connectionId()).thenReturn(connectionId);
    when(configurationRepository.getEffectiveBinding(configurationScopeId)).thenReturn(Optional.of(binding));
    var client = mock(SonarLintRpcClient.class);
    var underTest = new SkippedPluginsNotifierService(skippedPluginsRepository, configurationRepository, client);
    var event = new AnalysisFinishedEvent(UUID.randomUUID(), configurationScopeId, Duration.ZERO,
      Map.of(URI.create("file:///Foo.apex"), SonarLanguage.APEX), true, List.of(), false);

    underTest.onAnalysisFinished(event);

    var captor = ArgumentCaptor.forClass(DidSkipLoadingPluginParams.class);
    verify(client).didSkipLoadingPlugin(captor.capture());
    assertThat(captor.getValue()).extracting(
      DidSkipLoadingPluginParams::getConfigurationScopeId,
      DidSkipLoadingPluginParams::getLanguage,
      DidSkipLoadingPluginParams::getReason,
      DidSkipLoadingPluginParams::getMinVersion,
      DidSkipLoadingPluginParams::getCurrentVersion)
      .containsExactly(configurationScopeId, Language.APEX, DidSkipLoadingPluginParams.SkipReason.UNSATISFIED_JRE, "21", "17");
  }
}
