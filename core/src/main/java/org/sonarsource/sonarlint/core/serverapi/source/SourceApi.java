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
package org.sonarsource.sonarlint.core.serverapi.source;

import java.util.Optional;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.serverapi.HttpClient.Response;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class SourceApi {

  private static final Logger LOG = Loggers.get(SourceApi.class);

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
    try (Response r = serverApiHelper.get("/api/sources/raw?key=" + StringUtils.urlEncode(fileKey))) {
      return Optional.of(r.bodyAsString());
    } catch (Exception e) {
      LOG.debug("Unable to fetch source code of '" + fileKey + "'", e);
      return Optional.empty();
    }
  }

}
