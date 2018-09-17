/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.util.StringUtils;

public class StoragePaths {

  private static final int MAX_FOLDER_NAME_SIZE = 255;

  /**
   * Version of the storage. This should be incremented each time an incompatible change is made to the storage.
   */
  public static final String STORAGE_VERSION = "2";

  public static final String PLUGIN_REFERENCES_PB = "plugin_references.pb";
  public static final String PROPERTIES_PB = "properties.pb";
  public static final String PROJECT_CONFIGURATION_PB = "configuration.pb";
  public static final String PROJECT_PATH_PREFIXES_PB = "path_prefixes.pb";
  public static final String RULES_PB = "rules.pb";
  public static final String QUALITY_PROFILES_PB = "quality_profiles.pb";
  public static final String STORAGE_STATUS_PB = "storage_status.pb";
  public static final String SERVER_INFO_PB = "server_info.pb";
  public static final String ACTIVE_RULES_FOLDER = "active_rules";
  public static final String PROJECT_LIST_PB = "project_list.pb";
  public static final String SERVER_ISSUES_DIR = "server_issues";

  private final Path serverStorageRoot;
  private final Path globalStorageRoot;
  private final Path projectStorageRoot;

  public StoragePaths(ConnectedGlobalConfiguration configuration) {
    serverStorageRoot = configuration.getStorageRoot().resolve(encodeForFs(configuration.getServerId()));
    globalStorageRoot = serverStorageRoot.resolve("global");
    projectStorageRoot = serverStorageRoot.resolve("projects");
  }

  public Path getServerStorageRoot() {
    return serverStorageRoot;
  }

  public Path getGlobalStorageRoot() {
    return globalStorageRoot;
  }

  public Path getProjectStorageRoot(String projectKey) {
    return projectStorageRoot.resolve(encodeForFs(projectKey));
  }

  /**
   * Encodes a string to be used as a valid filename.
   * It should work in all OS and different names should never collide.
   * See SLCORE-148.
   */
  public static String encodeForFs(String name) {
    String encoded;
    try {
      encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("*", "%2A");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Unable to encode name: " + name, e);
    }
    if (encoded.length() > MAX_FOLDER_NAME_SIZE) {
      // Most FS will not support a folder name greater than 255
      String md5 = StringUtils.md5(name);
      return encoded.substring(0, MAX_FOLDER_NAME_SIZE - md5.length()) + md5;
    }
    return encoded;
  }

  public Path getProjectConfigurationPath(String projectKey) {
    return getProjectStorageRoot(projectKey).resolve(PROJECT_CONFIGURATION_PB);
  }

  public Path getProjectPathPrefixesPath(String projectKey) {
    return getProjectStorageRoot(projectKey).resolve(PROJECT_PATH_PREFIXES_PB);
  }

  public Path getProjectUpdateStatusPath(String projectKey) {
    return getProjectStorageRoot(projectKey).resolve(STORAGE_STATUS_PB);
  }

  public Path getPluginReferencesPath() {
    return getGlobalStorageRoot().resolve(PLUGIN_REFERENCES_PB);
  }

  public Path getGlobalPropertiesPath() {
    return getGlobalStorageRoot().resolve(PROPERTIES_PB);
  }

  public Path getProjectListPath() {
    return getGlobalStorageRoot().resolve(PROJECT_LIST_PB);
  }

  public Path getRulesPath() {
    return getGlobalStorageRoot().resolve(RULES_PB);
  }

  public Path getQProfilesPath() {
    return getGlobalStorageRoot().resolve(QUALITY_PROFILES_PB);
  }

  public Path getActiveRulesPath(String qProfileKey) {
    return getGlobalStorageRoot().resolve(ACTIVE_RULES_FOLDER).resolve(encodeForFs(qProfileKey) + ".pb");
  }

  public Path getStorageStatusPath() {
    return getGlobalStorageRoot().resolve(STORAGE_STATUS_PB);
  }

  public Path getServerInfosPath() {
    return getGlobalStorageRoot().resolve(SERVER_INFO_PB);
  }

  public Path getServerIssuesPath(String moduleKey) {
    return getProjectStorageRoot(moduleKey).resolve(SERVER_ISSUES_DIR);
  }
}
