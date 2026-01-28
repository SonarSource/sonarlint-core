/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
  public SQCProjectBindingsResponse getSQCProjectBindings(String url, SonarLintCancelMonitor cancelMonitor) {
    var encodedUrl = UrlUtils.urlEncode(url);

    var path = "/dop-translation/project-bindings?url=" + encodedUrl;

    try {
      var dto = serverApiHelper.apiGetJson(path, SQCProjectBindingsResponseDto.class, cancelMonitor);
      var bindings = dto.bindings();

      if (!bindings.isEmpty()) {
        return new SQCProjectBindingsResponse(bindings.get(0).projectId());
      }
    } catch (Exception e) {
      LOG.error("Error retrieving project bindings for URL: {}", url, e);
    }

    return null;
  }

  @CheckForNull
  public SQSProjectBindingsResponse getSQSProjectBindings(String url, SonarLintCancelMonitor cancelMonitor) {
    var encodedUrl = UrlUtils.urlEncode(url);
    var dto = serverApiHelper.getJson("/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl, SQSProjectBindingsResponseDto.class, cancelMonitor);
    var bindings = dto.projectBindings();
    if (!bindings.isEmpty()) {
      return new SQSProjectBindingsResponse(dto.projectBindings().get(0).projectId(), dto.projectBindings().get(0).projectKey());
    }
    return null;
  }
}
