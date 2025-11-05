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
package org.sonarsource.sonarlint.core.remediation.aicodefix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.reporting.PreviouslyRaisedFindingsRepository;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixSettings;
import org.sonarsource.sonarlint.core.serverconnection.repository.AiCodeFixSettingsRepository;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * This test verifies that AiCodeFixService.getFeature() reads settings
 * from AiCodeFixSettingsRepository (and does not rely on file-based StorageService).
 */
class AiCodeFixServiceTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void getFeature_reads_from_repository() {
    var connectionId = "conn-1";
    var projectKey = "project-A";
    var settings = new AiCodeFixSettings(
      Set.of("xml:S3421"),
      true,
      AiCodeFixFeatureEnablement.ENABLED_FOR_ALL_PROJECTS,
      Set.of(projectKey));

    var connectionRepository = mock(ConnectionConfigurationRepository.class);
    var configurationRepository = mock(ConfigurationRepository.class);
    var sonarQubeClientManager = mock(SonarQubeClientManager.class);
    var previouslyRaisedFindingsRepository = mock(PreviouslyRaisedFindingsRepository.class);
    var clientFileSystemService = mock(ClientFileSystemService.class);
    var eventPublisher = mock(ApplicationEventPublisher.class);
    var taintService = mock(TaintVulnerabilityTrackingService.class);
    var aiCodeFixSettingsRepository = mock(AiCodeFixSettingsRepository.class);
    when(aiCodeFixSettingsRepository.read(connectionId)).thenReturn(Optional.of(settings));

    var service = new AiCodeFixService(connectionRepository, configurationRepository, sonarQubeClientManager,
      previouslyRaisedFindingsRepository, clientFileSystemService, eventPublisher, taintService, aiCodeFixSettingsRepository);

    var binding = new Binding(connectionId, projectKey);

    Optional<AiCodeFixFeature> featureOpt = service.getFeature(binding);

    assertThat(featureOpt).isPresent();
    var feature = featureOpt.get();
    assertThat(feature.settings().supportedRules()).contains("xml:S3421");
    assertThat(feature.settings().isFeatureEnabled(projectKey)).isTrue();
  }
}
