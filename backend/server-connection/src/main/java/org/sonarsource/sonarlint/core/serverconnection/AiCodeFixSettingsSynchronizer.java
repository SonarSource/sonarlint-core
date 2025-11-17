/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.commons.storage.model.AiCodeFix;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;
import org.sonarsource.sonarlint.core.serverapi.features.Feature;
import org.sonarsource.sonarlint.core.serverapi.organization.ServerOrganization;

public class AiCodeFixSettingsSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  public static final Version MIN_SQS_VERSION_SUPPORTING_AI_CODEFIX = Version.create("2025.3");

  private final ConnectionStorage storage;
  private final OrganizationSynchronizer organizationSynchronizer;
  private final org.sonarsource.sonarlint.core.commons.storage.repository.AiCodeFixRepository aiCodeFixRepository;

  public AiCodeFixSettingsSynchronizer(ConnectionStorage storage, OrganizationSynchronizer organizationSynchronizer,
    org.sonarsource.sonarlint.core.commons.storage.repository.AiCodeFixRepository aiCodeFixRepository) {
    this.storage = storage;
    this.organizationSynchronizer = organizationSynchronizer;
    this.aiCodeFixRepository = aiCodeFixRepository;
  }

  public void synchronize(ServerApi serverApi, Version serverVersion, Set<String> projectKeys, SonarLintCancelMonitor cancelMonitor) {
    if (serverApi.isSonarCloud()) {
      synchronizeForSonarQubeCloud(serverApi, cancelMonitor);
    } else {
      synchronizeForSonarQubeServer(serverApi, serverVersion, projectKeys, cancelMonitor);
    }
  }

  private void synchronizeForSonarQubeCloud(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    var userOrganizations = serverApi.isSonarCloud() ? serverApi.organization().listUserOrganizations(cancelMonitor) : List.<ServerOrganization>of();
    if (userBelongsToOrganization(serverApi, userOrganizations)) {
      try {
        var supportedRules = serverApi.fixSuggestions().getSupportedRules(cancelMonitor);
        var organization = organizationSynchronizer.readOrSynchronizeOrganization(serverApi, cancelMonitor);
        var organizationConfig = serverApi.fixSuggestions().getOrganizationConfigs(organization.id(), cancelMonitor);
        var aiCodeFixConfiguration = organizationConfig.aiCodeFix();
        var enabledProjectKeys = aiCodeFixConfiguration.enabledProjectKeys();
        var enabled = enabledProjectKeys == null ? Set.<String>of() : enabledProjectKeys;
        var entity = new AiCodeFix(
          storage.connectionId(),
          supportedRules.rules(),
          aiCodeFixConfiguration.organizationEligible(),
          AiCodeFix.Enablement.valueOf(aiCodeFixConfiguration.enablement().name()),
          enabled);
        aiCodeFixRepository.upsert(entity);
      } catch (Exception e) {
        LOG.error("Error synchronizing AI CodeFix settings for SonarQube Cloud", e);
      }
    }
  }

  private void synchronizeForSonarQubeServer(ServerApi serverApi, Version serverVersion, Set<String> projectKeys, SonarLintCancelMonitor cancelMonitor) {
    try {
      if (serverVersion.satisfiesMinRequirement(MIN_SQS_VERSION_SUPPORTING_AI_CODEFIX) && serverApi.features().list(cancelMonitor).contains(Feature.AI_CODE_FIX)) {
        var supportedRules = serverApi.fixSuggestions().getSupportedRules(cancelMonitor);
        var enabledProjectKeys = projectKeys.stream()
          .filter(projectKey -> serverApi.component().getProject(projectKey, cancelMonitor).filter(ServerProject::isAiCodeFixEnabled).isPresent()).collect(Collectors.toSet());
        var entity = new AiCodeFix(
          storage.connectionId(),
          supportedRules.rules(),
          true,
          AiCodeFix.Enablement.ENABLED_FOR_SOME_PROJECTS,
          enabledProjectKeys);
        aiCodeFixRepository.upsert(entity);
      }
    } catch (Exception e) {
      LOG.error("Error synchronizing AI CodeFix settings for SonarQube Server", e);
    }
  }

  private static boolean userBelongsToOrganization(ServerApi serverApi, List<ServerOrganization> userOrganizations) {
    return serverApi.getOrganizationKey().filter(orgKey -> userOrganizations.stream().anyMatch(org -> org.getKey().equals(orgKey))).isPresent();
  }
}
