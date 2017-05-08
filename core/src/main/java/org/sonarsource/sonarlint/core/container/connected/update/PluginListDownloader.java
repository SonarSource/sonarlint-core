/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.sonarsource.sonarlint.core.client.api.connected.SonarAnalyzer;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.model.DefaultSonarAnalyzer;
import org.sonarsource.sonarlint.core.plugin.PluginCopier;
import org.sonarsource.sonarlint.core.plugin.Version;
import org.sonarsource.sonarlint.core.util.VersionUtils;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

public class PluginListDownloader {
  public static final String WS_PATH = "/api/plugins/installed";
  public static final String WS_PATH_LTS = "/deploy/plugins/index.txt";

  private final SonarLintWsClient wsClient;
  private final PluginVersionChecker pluginVersionChecker;

  public PluginListDownloader(SonarLintWsClient wsClient, PluginVersionChecker pluginVersionChecker) {
    this.wsClient = wsClient;
    this.pluginVersionChecker = pluginVersionChecker;
  }

  public List<SonarAnalyzer> downloadPluginList(String serverVersion) {
    List<SonarAnalyzer> analyzers = new LinkedList<>();
    boolean compatibleFlagPresent = Version.create(serverVersion).compareToIgnoreQualifier(Version.create("6.0")) >= 0;
    String responseStr;
    try (WsResponse response = wsClient.get(WS_PATH_LTS)) {
      responseStr = response.content();
    }

    Scanner scanner = new Scanner(responseStr);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      String[] fields = StringUtils.split(line, ",");
      String[] nameAndHash = StringUtils.split(fields[fields.length - 1], "|");

      String key = fields[0];
      String filename = nameAndHash[0];
      String hash = nameAndHash[1];
      String version = VersionUtils.getJarVersion(filename);
      String minVersion = pluginVersionChecker.getMinimumVersion(key);
      boolean sonarlintCompatible;
      if (compatibleFlagPresent) {
        sonarlintCompatible = PluginCopier.isWhitelisted(key) || "true".equals(fields[1]);
      } else {
        sonarlintCompatible = PluginCopier.isWhitelisted(key);
      }
      DefaultSonarAnalyzer analyzer = new DefaultSonarAnalyzer(key, filename, hash, version, sonarlintCompatible);
      analyzer.minimumVersion(minVersion);
      analyzers.add(analyzer);
    }

    scanner.close();
    return analyzers;
  }

  public static class SonarQubePlugin {
    private String key;
    private String filename;
    private String version;
    private String hash;
    private Boolean sonarlintCompatibleFlag;

    public SonarQubePlugin(String key, String filename, String hash, @Nullable String version) {
      this(key, filename, hash, version, null);
    }

    public SonarQubePlugin(String key, String filename, String hash, @Nullable String version, @Nullable Boolean sonarLintFlag) {
      this.key = key;
      this.filename = filename;
      this.hash = hash;
      this.version = version;
      this.sonarlintCompatibleFlag = sonarLintFlag;
    }

    @CheckForNull
    public Boolean sonarlintCompatibleFlag() {
      return sonarlintCompatibleFlag;
    }

    public String key() {
      return key;
    }

    public String hash() {
      return hash;
    }

    public String filename() {
      return filename;
    }

    @CheckForNull
    public String version() {
      return version;
    }

    @Override
    public String toString() {
      return key;
    }
  }
}
