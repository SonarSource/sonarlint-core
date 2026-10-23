/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.sca;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;

public class ScaApi {

  private static final Gson GSON = new Gson();

  private final ServerApiHelper serverApiHelper;

  public ScaApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public GetIssuesReleasesResponse getIssuesReleases(String projectKey, String branchKey, SonarLintCancelMonitor cancelMonitor) {
    var urlPrefix = serverApiHelper.isSonarCloud() ? "" : "/api/v2";
    var url = urlPrefix + "/sca/issues-releases?projectKey=" +
      UrlUtils.urlEncode(projectKey) +
      "&branchKey=" +
      UrlUtils.urlEncode(branchKey);

    var allIssuesReleases = new ArrayList<GetIssuesReleasesResponse.IssuesRelease>();

    if (serverApiHelper.isSonarCloud()) {
      serverApiHelper.apiGetPaginated(
        url,
        ScaApi::parseIssuesReleasesPage,
        r -> r.page().total(),
        RawIssuesReleasesPage::issuesReleases,
        item -> allIssuesReleases.add(parseIssueRelease(item)),
        false,
        cancelMonitor,
        "pageIndex",
        "pageSize");
    } else {
      serverApiHelper.getPaginated(
        url,
        ScaApi::parseIssuesReleasesPage,
        r -> r.page().total(),
        RawIssuesReleasesPage::issuesReleases,
        item -> allIssuesReleases.add(parseIssueRelease(item)),
        false,
        cancelMonitor,
        "pageIndex",
        "pageSize");
    }
    return new GetIssuesReleasesResponse(allIssuesReleases, new GetIssuesReleasesResponse.Page(allIssuesReleases.size()));
  }

  private static RawIssuesReleasesPage parseIssuesReleasesPage(InputStream response) {
    var root = JsonParser.parseReader(new InputStreamReader(response, StandardCharsets.UTF_8));
    if (!root.isJsonObject()) {
      throw new JsonParseException("Expected an SCA response object");
    }
    var object = root.getAsJsonObject();
    var items = object.get("issuesReleases");
    var page = object.get("page");
    if (items == null || !items.isJsonArray() || page == null || !page.isJsonObject()) {
      throw new JsonParseException("Expected SCA issuesReleases array and page object");
    }
    return new RawIssuesReleasesPage(items.getAsJsonArray().asList(), GSON.fromJson(page, GetIssuesReleasesResponse.Page.class));
  }

  private static GetIssuesReleasesResponse.IssuesRelease parseIssueRelease(JsonElement item) {
    if (!item.isJsonObject()) {
      throw new InvalidServerItemException("Expected an SCA issue release object");
    }
    try {
      return GSON.fromJson(item, GetIssuesReleasesResponse.IssuesRelease.class);
    } catch (JsonParseException | IllegalArgumentException e) {
      throw new InvalidServerItemException("Unable to decode SCA issue release", e);
    }
  }

  private record RawIssuesReleasesPage(List<JsonElement> issuesReleases, GetIssuesReleasesResponse.Page page) {
  }

  public void changeStatus(UUID issueReleaseKey, String transitionKey, @Nullable String comment, SonarLintCancelMonitor cancelMonitor) {
    var body = new ChangeStatusRequestBody(issueReleaseKey.toString(), transitionKey, comment);
    var urlPrefix = serverApiHelper.isSonarCloud() ? "" : "/api/v2";
    var url = urlPrefix + "/sca/issues-releases/change-status";

    if (serverApiHelper.isSonarCloud()) {
      serverApiHelper.apiPostJson(url, body, cancelMonitor);
    } else {
      serverApiHelper.postJson(url, body, cancelMonitor);
    }
  }

  private record ChangeStatusRequestBody(String issueReleaseKey, String transitionKey, @Nullable String comment) {
  }

  public GetScaEnablementResponse isScaEnabled(SonarLintCancelMonitor cancelMonitor) {
    var organizationKey = serverApiHelper.getOrganizationKey();
    if (organizationKey.isEmpty()) {
      return new GetScaEnablementResponse(false);
    }
    try {
      return serverApiHelper.apiGetJson("/sca/feature-enabled?organization=" + UrlUtils.urlEncode(organizationKey.get()),
        GetScaEnablementResponse.class, cancelMonitor);
    } catch (Exception e) {
      return new GetScaEnablementResponse(false);
    }
  }

}
