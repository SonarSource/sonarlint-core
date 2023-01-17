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
package org.sonarsource.sonarlint.core.serverapi.source;

import java.util.Optional;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;

public class SourceApi {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper serverApiHelper;

  public SourceApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  /**
   * Fetch source code of the file with specified key.
   * If the component doesn't exist or it exists but has no source, an empty String is returned.
   *
   * @param key project key, or file key.
   */
  public Optional<String> getRawSourceCode(String fileKey) {
    try (var r = serverApiHelper.get("/api/sources/raw?key=" + UrlUtils.urlEncode(fileKey))) {
      return Optional.of(r.bodyAsString());
    } catch (Exception e) {
      LOG.debug("Unable to fetch source code of '" + fileKey + "'", e);
      return Optional.empty();
    }
  }

}
