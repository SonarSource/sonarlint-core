/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.SonarQubeClientManager;
import org.sonarsource.sonarlint.core.commons.Binding;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.storage.SonarLintDatabase;
import org.sonarsource.sonarlint.core.fs.ClientFileSystemService;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.reporting.PreviouslyRaisedFindingsRepository;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFix;
import org.sonarsource.sonarlint.core.serverconnection.aicodefix.AiCodeFixRepository;
import org.sonarsource.sonarlint.core.tracking.TaintVulnerabilityTrackingService;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * This test verifies that AiCodeFixService.getFeature() reads settings
 * from the H2-backed AiCodeFixRepository (and does not rely on file-based StorageService).
 */
class AiCodeFixServiceTest {

  @RegisterExtension
  static SonarLintLogTester logTester = new SonarLintLogTester();

  @TempDir
  Path tempDir;

  private SonarLintDatabase db;

  @AfterEach
  void tearDown() {
    if (db != null) {
      db.shutdown();
    }
  }

  @Test
  void getFeature_reads_from_h2_repository() {
    db = new SonarLintDatabase(tempDir);
    var aiCodeFixRepo = new AiCodeFixRepository(db.dsl());

    var connectionId = "conn-1";
    var projectKey = "project-A";
    aiCodeFixRepo.upsert(new AiCodeFix(
      connectionId,
      Set.of("xml:S3421"),
      true,
      AiCodeFix.Enablement.ENABLED_FOR_ALL_PROJECTS,
      Set.of(projectKey)));

    var connectionRepository = mock(ConnectionConfigurationRepository.class);
    var configurationRepository = mock(ConfigurationRepository.class);
    var sonarQubeClientManager = mock(SonarQubeClientManager.class);
    var previouslyRaisedFindingsRepository = mock(PreviouslyRaisedFindingsRepository.class);
    var clientFileSystemService = mock(ClientFileSystemService.class);
    var eventPublisher = mock(ApplicationEventPublisher.class);
    var taintService = mock(TaintVulnerabilityTrackingService.class);

    var service = new AiCodeFixService(connectionRepository, configurationRepository, sonarQubeClientManager, previouslyRaisedFindingsRepository, clientFileSystemService,
      eventPublisher, taintService, aiCodeFixRepo);

    var binding = new Binding(connectionId, projectKey);

    Optional<AiCodeFixFeature> featureOpt = service.getFeature(binding);

    assertThat(featureOpt).isPresent();
    var feature = featureOpt.get();
    assertThat(feature.settings().supportedRules()).contains("xml:S3421");
    assertThat(feature.settings().isFeatureEnabled(projectKey)).isTrue();
  }
}
