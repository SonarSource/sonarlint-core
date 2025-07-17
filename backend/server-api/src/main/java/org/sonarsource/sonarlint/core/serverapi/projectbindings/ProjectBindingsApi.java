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
package org.sonarsource.sonarlint.core.serverapi.projectbindings;

import com.google.gson.Gson;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class ProjectBindingsApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper serverApiHelper;

  public ProjectBindingsApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  @CheckForNull
  public ProjectBindingsResponse getProjectBindings(String url, SonarLintCancelMonitor cancelMonitor) {
    var encodedUrl = UrlUtils.urlEncode(url);
    var path = "/dop-translation/project-bindings?url=" + encodedUrl;

    try (var response = serverApiHelper.apiGet(path, cancelMonitor)) {
      if (response.isSuccessful()) {
        var responseBody = response.bodyAsString();
        var dto = new Gson().fromJson(responseBody, ProjectBindingsResponseDto.class);
        var bindings = dto.bindings();

        if (!bindings.isEmpty()) {
          return new ProjectBindingsResponse(bindings.get(0).projectId());
        }
      } else {
        LOG.warn("Failed to retrieve project bindings for URL: {} (status: {})", url, response.code());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving project bindings for URL: {}", url, e);
    }

    return null;
  }
}
