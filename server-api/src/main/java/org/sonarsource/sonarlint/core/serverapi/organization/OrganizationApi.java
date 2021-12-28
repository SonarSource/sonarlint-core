/*
 * SonarLint Server API
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.organization;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.sonarqube.ws.Organizations;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.system.ServerVersionAndStatusChecker;

public class OrganizationApi {
  private final ServerApiHelper helper;

  public OrganizationApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<ServerOrganization> listUserOrganizations(ProgressMonitor progress) {
    var serverChecker = new ServerVersionAndStatusChecker(helper);
    return listUserOrganizations(serverChecker, progress);
  }

  public Optional<ServerOrganization> getOrganization(String organizationKey, ProgressMonitor progress) {
    var serverChecker = new ServerVersionAndStatusChecker(helper);
    checkServer(serverChecker, progress);
    return fetchOrganization(organizationKey, progress.subProgress(0.2f, 1.0f, "Fetch organization"));
  }

  private List<ServerOrganization> listUserOrganizations(ServerVersionAndStatusChecker serverChecker, ProgressMonitor progress) {
    checkServer(serverChecker, progress);
    return fetchUserOrganizations(progress.subProgress(0.2f, 1.0f, "Fetch organizations"));
  }

  private static void checkServer(ServerVersionAndStatusChecker serverChecker, ProgressMonitor progress) {
    progress.setProgressAndCheckCancel("Check server version", 0.1f);
    serverChecker.checkVersionAndStatus();
    progress.setProgressAndCheckCancel("Fetch organizations", 0.2f);
  }

  public Optional<ServerOrganization> fetchOrganization(String organizationKey, ProgressMonitor progress) {
    String url = "api/organizations/search.protobuf?organizations=" + UrlUtils.urlEncode(organizationKey);
    return getPaginatedOrganizations(url, progress)
      .stream()
      .findFirst();
  }

  private List<ServerOrganization> fetchUserOrganizations(ProgressMonitor progress) {
    var url = "api/organizations/search.protobuf?member=true";
    return getPaginatedOrganizations(url, progress);
  }

  private List<ServerOrganization> getPaginatedOrganizations(String url, ProgressMonitor progress) {
    List<ServerOrganization> result = new ArrayList<>();

    helper.getPaginated(url,
      Organizations.SearchWsResponse::parseFrom,
      Organizations.SearchWsResponse::getPaging,
      Organizations.SearchWsResponse::getOrganizationsList,
      org -> result.add(new DefaultRemoteOrganization(org)),
      false,
      progress);

    return result;
  }
}
