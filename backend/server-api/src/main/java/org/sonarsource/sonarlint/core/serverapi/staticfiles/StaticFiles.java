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
package org.sonarsource.sonarlint.core.serverapi.staticfiles;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

/**
 * Client for files published under the server {@code /static} prefix.
 */
public class StaticFiles {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public StaticFiles(ServerApiHelper helper) {
    this.helper = helper;
  }

  public void get(String path, Consumer<InputStream> consumer, SonarLintCancelMonitor cancelMonitor) {
    var start = System.currentTimeMillis();
    try (var response = helper.get(encodePath(path), cancelMonitor)) {
      consumer.accept(response.bodyAsStream());
      LOG.info("Downloaded '{}' in {}ms", path, System.currentTimeMillis() - start);
    }
  }

  private static String encodePath(String path) {
    return Arrays.stream(path.split("/", -1)).map(StaticFiles::encodeSegment).collect(Collectors.joining("/"));
  }

  private static String encodeSegment(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

}
