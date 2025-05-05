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
package org.sonarsource.sonarlint.core.serverapi.features;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;

public class FeaturesApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public FeaturesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public Set<Feature> list(SonarLintCancelMonitor cancelMonitor) {
    try (var response = helper.rawGet("api/features/list", cancelMonitor)) {
      var responseStr = response.bodyAsString();
      var featureKeys = new Gson().fromJson(responseStr, String[].class);
      return Arrays.stream(featureKeys).flatMap(key -> Feature.fromKey(key).stream()).collect(Collectors.toSet());
    } catch (Exception e) {
      LOG.error("Error while fetching the list of features", e);
      throw new UnexpectedBodyException(e);
    }
  }
}
