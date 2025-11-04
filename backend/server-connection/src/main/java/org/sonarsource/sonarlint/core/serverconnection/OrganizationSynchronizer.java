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

import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverconnection.repository.OrganizationRepository;

public class OrganizationSynchronizer {
  private final OrganizationRepository organizationRepository;
  private final String connectionId;

  public OrganizationSynchronizer(OrganizationRepository organizationRepository, String connectionId) {
    this.organizationRepository = organizationRepository;
    this.connectionId = connectionId;
  }

  // should be called only in the context of SonarQube Cloud
  public Organization readOrSynchronizeOrganization(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    return organizationRepository.read(connectionId)
      .orElseGet(() -> synchronize(serverApi, cancelMonitor));
  }

  private Organization synchronize(ServerApi serverApi, SonarLintCancelMonitor cancelMonitor) {
    var organizationDto = serverApi.organization().getOrganizationByKey(cancelMonitor);
    var organization = new Organization(organizationDto.id(), organizationDto.uuidV4());
    organizationRepository.store(connectionId, organization);
    return organization;
  }
}
