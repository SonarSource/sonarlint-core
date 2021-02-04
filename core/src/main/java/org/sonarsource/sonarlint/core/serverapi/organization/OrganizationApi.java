/*
 * SonarLint Core - Implementation
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
import javax.annotation.Nullable;
import org.sonarqube.ws.Organizations;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultRemoteOrganization;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class OrganizationApi {
  private final ServerApiHelper helper;

  public OrganizationApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<ServerOrganization> listUserOrganizations(@Nullable ProgressMonitor monitor) {
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(helper);
    return listUserOrganizations(serverChecker, new ProgressWrapper(monitor));
  }

  public Optional<ServerOrganization> getOrganization(String organizationKey, @Nullable ProgressMonitor monitor) {
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(helper);
    return getOrganization(serverChecker, organizationKey, new ProgressWrapper(monitor));
  }

  Optional<ServerOrganization> getOrganization(ServerVersionAndStatusChecker serverChecker, String organizationKey, ProgressWrapper progress) {
    try {
      checkServer(serverChecker, progress);
      return fetchOrganization(organizationKey, progress.subProgress(0.2f, 1.0f, "Fetch organization"));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  List<ServerOrganization> listUserOrganizations(ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    try {
      checkServer(serverChecker, progress);
      return fetchUserOrganizations(progress.subProgress(0.2f, 1.0f, "Fetch organizations"));
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static void checkServer(ServerVersionAndStatusChecker serverChecker, ProgressWrapper progress) {
    progress.setProgressAndCheckCancel("Check server version", 0.1f);
    serverChecker.checkVersionAndStatus();
    progress.setProgressAndCheckCancel("Fetch organizations", 0.2f);
  }

  public Optional<ServerOrganization> fetchOrganization(String organizationKey, ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf?organizations=" + StringUtils.urlEncode(organizationKey);
    return getPaginatedOrganizations(url, progress)
      .stream()
      .findFirst();
  }

  private List<ServerOrganization> fetchUserOrganizations(ProgressWrapper progress) {
    String url = "api/organizations/search.protobuf?member=true";
    return getPaginatedOrganizations(url, progress);
  }

  private List<ServerOrganization> getPaginatedOrganizations(String url, ProgressWrapper progress) {
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
