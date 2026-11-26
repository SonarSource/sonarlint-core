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
    download(url(pluginPath(key, hash + ".jar")), key, pluginFileConsumer, cancelMonitor);
  }

  public void getResource(String key, String staticResourceFolder, String resourcePath, Consumer<InputStream> resourceConsumer, SonarLintCancelMonitor cancelMonitor) {
    download(url(pluginPath(key, staticResourceFolder + "/" + resourcePath)), key + ":" + resourcePath, resourceConsumer, cancelMonitor);
  }

  private String url(String path) {
    var cdnBaseUrl = helper.getCdnBaseUrl();
    if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
      return (cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl) + path;
    }
    return scannerUrl(helper.getBaseUrl(), path);
  }

  private void download(String url, String description, Consumer<InputStream> consumer, SonarLintCancelMonitor cancelMonitor) {
    var start = System.currentTimeMillis();
    try (var response = helper.getAnonymousUrl(url, cancelMonitor)) {
      consumer.accept(response.bodyAsStream());
      var duration = System.currentTimeMillis() - start;
      LOG.info("Downloaded '{}' in {}ms", description, duration);
    }
  }

  static String buildDownloadUrl(String baseUrl, String key, String hash) {
    return scannerUrl(baseUrl, pluginPath(key, hash + ".jar"));
  }

  static String buildResourceUrl(String baseUrl, String key, String staticResourceFolder, String resourcePath) {
    return scannerUrl(baseUrl, pluginPath(key, staticResourceFolder + "/" + resourcePath));
  }

  private static String pluginPath(String key, String versionPath) {
    return "/plugins/" + key + "/versions/" + versionPath;
  }

  private static String scannerUrl(String baseUrl, String path) {
    var baseUri = URI.create(baseUrl);
    var host = baseUri.getHost();
    if (host == null) {
      throw new IllegalArgumentException("SonarQube Cloud URL must contain a host: " + baseUrl);
    }
    try {
      return new URI(baseUri.getScheme(), null, "scanner." + host, baseUri.getPort(), path, null, null).toASCIIString();
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to build the SonarQube Cloud plugin download URL", e);
    }
  }

}
