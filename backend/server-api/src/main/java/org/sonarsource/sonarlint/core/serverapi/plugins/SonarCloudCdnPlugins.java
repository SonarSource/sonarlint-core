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
package org.sonarsource.sonarlint.core.serverapi.plugins;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

/**
 * Downloads SonarQube Cloud analyzer jars from the CloudFront CDN ({@code scanner.<host>}),
 * which is not part of the SonarQube Web API.
 */
public class SonarCloudCdnPlugins {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ServerApiHelper helper;

  public SonarCloudCdnPlugins(ServerApiHelper helper) {
    this.helper = helper;
  }

  public void getPlugin(String key, String hash, Consumer<InputStream> pluginFileConsumer, SonarLintCancelMonitor cancelMonitor) {
    var url = buildDownloadUrl(helper.getBaseUrl(), key, hash);
    var start = System.currentTimeMillis();
    try (var response = helper.getAnonymousUrl(url, cancelMonitor)) {
      pluginFileConsumer.accept(response.bodyAsStream());
      var duration = System.currentTimeMillis() - start;
      LOG.info("Downloaded '{}' in {}ms", key, duration);
    }
  }

  static String buildDownloadUrl(String baseUrl, String key, String hash) {
    var baseUri = URI.create(baseUrl);
    var host = baseUri.getHost();
    if (host == null) {
      throw new IllegalArgumentException("SonarQube Cloud URL must contain a host: " + baseUrl);
    }
    try {
      var path = "/plugins/" + key + "/versions/" + hash + ".jar";
      return new URI(baseUri.getScheme(), null, "scanner." + host, baseUri.getPort(), path, null, null).toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to build the SonarQube Cloud plugin download URL", e);
    }
  }

}
