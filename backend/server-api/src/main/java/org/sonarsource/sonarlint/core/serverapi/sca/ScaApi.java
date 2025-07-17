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
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

import static org.sonarsource.sonarlint.core.http.HttpClient.JSON_CONTENT_TYPE;

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

  public GetIssueReleaseResponse getIssueRelease(String key, SonarLintCancelMonitor cancelMonitor) {
    var url = "/api/v2/sca/issues-releases/" + UrlUtils.urlEncode(key);
    try (var response = serverApiHelper.get(url, cancelMonitor)) {
      return new Gson().fromJson(new InputStreamReader(response.bodyAsStream(), StandardCharsets.UTF_8), GetIssueReleaseResponse.class);
    }
  }

  public void changeStatus(UUID issueReleaseKey, String transitionKey, String comment, SonarLintCancelMonitor cancelMonitor) {
    var body = new ChangeStatusRequestBody(issueReleaseKey.toString(), transitionKey, comment);
    var url = "/api/v2/sca/issues-releases/change-status";

    serverApiHelper.post(url, JSON_CONTENT_TYPE, body.toJson(), cancelMonitor);
  }

  private record ChangeStatusRequestBody(String issueReleaseKey, String transitionKey, String comment) {
    public String toJson() {
      return new Gson().toJson(this);
    }
  }

}
