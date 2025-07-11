/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.sca;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class ScaApi {

  private final ServerApiHelper serverApiHelper;

  public ScaApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public GetIssuesReleasesResponse getIssuesReleases(String projectKey, String branchName, SonarLintCancelMonitor cancelMonitor) {
    var url = "/api/v2/sca/issues-releases?projectKey=" +
      UrlUtils.urlEncode(projectKey) +
      "&branchName=" +
      UrlUtils.urlEncode(branchName);

    var allIssuesReleases = new ArrayList<GetIssuesReleasesResponse.IssuesRelease>();

    serverApiHelper.getPaginated(
      url,
      response -> new Gson().fromJson(new InputStreamReader(response, StandardCharsets.UTF_8), GetIssuesReleasesResponse.class),
      r -> r.page().total(),
      GetIssuesReleasesResponse::issuesReleases,
      allIssuesReleases::add,
      false,
      cancelMonitor,
      "pageIndex",
      "pageSize");
    return new GetIssuesReleasesResponse(allIssuesReleases, new GetIssuesReleasesResponse.Page(allIssuesReleases.size()));
  }

}
