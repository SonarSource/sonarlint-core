/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

public class AiCodeFixSettingsSynchronizer {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConnectionStorage storage;
  private final OrganizationSynchronizer organizationSynchronizer;

  public AiCodeFixSettingsSynchronizer(ConnectionStorage storage, OrganizationSynchronizer organizationSynchronizer) {
    this.storage = storage;
    this.organizationSynchronizer = organizationSynchronizer;
  }

  public void synchronize(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    if (serverApi.isSonarCloud()) {
      try {
        var supportedRules = serverApi.fixSuggestions().getSupportedRules(cancelMonitor);
        var organization = organizationSynchronizer.readOrSynchronizeOrganization(serverApi, cancelMonitor);
        var organizationConfig = serverApi.fixSuggestions().getOrganizationConfigs(organization.id(), cancelMonitor);
        storage.aiCodeFix().store(new AiCodeFixSettings(supportedRules.rules(), organizationConfig.organizationEligible(),
          AiCodeFixFeatureEnablement.valueOf(organizationConfig.enablement().name()), organizationConfig.enabledProjectKeys()));
      } catch (Exception e) {
        LOG.error("Error synchronizing AI CodeFix settings", e);
      }
    }
  }
}
