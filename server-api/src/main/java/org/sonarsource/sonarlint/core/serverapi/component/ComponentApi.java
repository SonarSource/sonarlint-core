/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2023 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;

public class ComponentApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public ComponentApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public List<String> getAllFileKeys(String projectKey, ProgressMonitor progress) {
    var path = buildAllFileKeysPath(projectKey);
    List<String> files = new ArrayList<>();

    helper.getPaginated(path,
      Components.TreeWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      Components.TreeWsResponse::getComponentsList,
      component -> files.add(component.getKey()), false, progress);
    return files;
  }

  private String buildAllFileKeysPath(String projectKey) {
    var url = new StringBuilder();
    url.append("api/components/tree.protobuf?qualifiers=FIL,UTS&");
    url.append("component=").append(UrlUtils.urlEncode(projectKey));
    helper.getOrganizationKey().ifPresent(org -> url.append("&organization=").append(UrlUtils.urlEncode(org)));
    return url.toString();
  }

  public Optional<ServerProject> getProject(String projectKey) {
    return fetchComponent(projectKey).map(component -> new DefaultRemoteProject(component.getKey(), component.getName()));
  }

  public List<ServerProject> getAllProjects(ProgressMonitor progress) {
    List<ServerProject> serverProjects = new ArrayList<>();
    helper.getPaginated(getAllProjectsUrl(),
      Components.SearchWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      Components.SearchWsResponse::getComponentsList,
      project -> serverProjects.add(new DefaultRemoteProject(project.getKey(), project.getName())),
      true,
      progress);
    return serverProjects;
  }

  private String getAllProjectsUrl() {
    var searchUrl = new StringBuilder();
    searchUrl.append("api/components/search.protobuf?qualifiers=TRK");
    helper.getOrganizationKey()
      .ifPresent(org -> searchUrl.append("&organization=").append(UrlUtils.urlEncode(org)));
    return searchUrl.toString();
  }

  private Optional<Component> fetchComponent(String componentKey) {
    return fetchComponent(componentKey, response -> {
      var wsComponent = response.getComponent();
      return new Component(wsComponent.getKey(), wsComponent.getName());
    });
  }

  public Optional<String> fetchFirstAncestorKey(String componentKey) {
    return fetchComponent(componentKey, response -> response.getAncestorsList().stream().map(Components.Component::getKey).findFirst().orElse(null));
  }

  private <T> Optional<T> fetchComponent(String componentKey, Function<Components.ShowWsResponse, T> responseConsumer) {
    return ServerApiHelper.processTimed(
      () -> helper.rawGet("api/components/show.protobuf?component=" + UrlUtils.urlEncode(componentKey)),
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
