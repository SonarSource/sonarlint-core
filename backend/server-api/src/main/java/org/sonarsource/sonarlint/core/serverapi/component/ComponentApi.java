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
package org.sonarsource.sonarlint.core.serverapi.component;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

public class ComponentApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private static final String ORGANIZATION_PARAM = "&organization=";

  private final ServerApiHelper helper;

  public ComponentApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<String> getAllFileKeys(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    var path = buildAllFileKeysPath(projectKey);
    List<String> files = new ArrayList<>();

    helper.getPaginated(path,
      Components.TreeWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      Components.TreeWsResponse::getComponentsList,
      component -> files.add(component.getKey()), false, cancelMonitor);
    return files;
  }

  private String buildAllFileKeysPath(String projectKey) {
    var url = new StringBuilder();
    url.append("api/components/tree.protobuf?qualifiers=FIL,UTS&");
    url.append("component=").append(UrlUtils.urlEncode(projectKey));
    helper.getOrganizationKey().ifPresent(org -> url.append(ORGANIZATION_PARAM).append(UrlUtils.urlEncode(org)));
    return url.toString();
  }

  public Optional<ServerProject> getProject(String projectKey, SonarLintCancelMonitor cancelMonitor) {
    return fetchComponent(projectKey, cancelMonitor).map(component -> new ServerProject(component.key(), component.name(), component.isAiCodeFixEnabled()));
  }

  public List<ServerProject> getAllProjects(SonarLintCancelMonitor cancelMonitor) {
    List<ServerProject> serverProjects = new ArrayList<>();
    helper.getPaginated(getAllProjectsUrl(),
      Components.SearchWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      Components.SearchWsResponse::getComponentsList,
      project -> serverProjects.add(new ServerProject(project.getKey(), project.getName(), project.getIsAiCodeFixEnabled())),
      true,
      cancelMonitor);
    return serverProjects;
  }

  private String getAllProjectsUrl() {
    var searchUrl = new StringBuilder();
    searchUrl.append("api/components/search.protobuf?qualifiers=TRK");
    helper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append(ORGANIZATION_PARAM).append(UrlUtils.urlEncode(org)));
    return searchUrl.toString();
  }

  @CheckForNull
  public SearchProjectResponse searchProjects(String projectId, SonarLintCancelMonitor cancelMonitor) {
    var encodedProjectId = UrlUtils.urlEncode(projectId);
    var organization = helper.getOrganizationKey();

    if (organization.isEmpty()) {
      LOG.warn("Organization key is not set, cannot search projects for ID: {}", projectId);
      return null;
    }
    var path = "/api/components/search_projects?projectIds=" + encodedProjectId + ORGANIZATION_PARAM + organization.get();

    try (var response = helper.rawGet(path, cancelMonitor)) {
      if (response.isSuccessful()) {
        var searchResponse = new Gson().fromJson(response.bodyAsString(), SearchProjectResponseDto.class);

        return searchResponse.components().stream()
          .findFirst()
          .map(component -> new SearchProjectResponse(component.key(), component.name()))
          .orElse(null);
      } else {
        LOG.warn("Failed to retrieve project for ID: {} (status: {})", projectId, response.code());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving project for ID: {}", projectId, e);
    }

    return null;
  }

  private Optional<Component> fetchComponent(String componentKey, SonarLintCancelMonitor cancelMonitor) {
    return fetchComponent(componentKey, response -> {
      var wsComponent = response.getComponent();
      return new Component(wsComponent.getKey(), wsComponent.getName(), wsComponent.getIsAiCodeFixEnabled());
    }, cancelMonitor);
  }

  public Optional<String> fetchFirstAncestorKey(String componentKey, SonarLintCancelMonitor cancelMonitor) {
    return fetchComponent(componentKey, response -> response.getAncestorsList().stream().map(Components.Component::getKey).findFirst().orElse(null), cancelMonitor);
  }

  private <T> Optional<T> fetchComponent(String componentKey, Function<Components.ShowWsResponse, T> responseConsumer, SonarLintCancelMonitor cancelMonitor) {
    return ServerApiHelper.processTimed(
      () -> helper.rawGet("api/components/show.protobuf?component=" + UrlUtils.urlEncode(componentKey), cancelMonitor),
      response -> {
        if (response.isSuccessful()) {
          var wsResponse = Components.ShowWsResponse.parseFrom(response.bodyAsStream());
          return Optional.ofNullable(responseConsumer.apply(wsResponse));
        }
        return Optional.empty();
      },
      duration -> LOG.debug("Downloaded project details in {}ms", duration));
  }
}
