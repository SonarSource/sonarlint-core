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
package org.sonarsource.sonarlint.core.serverapi;

import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.organization.OrganizationApi;
import org.sonarsource.sonarlint.core.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarlint.core.serverapi.project.ProjectApi;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfileApi;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi;
import org.sonarsource.sonarlint.core.serverapi.settings.SettingsApi;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.system.SystemApi;

public class ServerApi {
  private final ServerApiHelper helper;

  public ServerApi(EndpointParams endpoint, HttpClient client) {
    this(new ServerApiHelper(endpoint, client));
  }

  public ServerApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public HotspotApi hotspot() {
    return new HotspotApi(helper);
  }

  public OrganizationApi organization() {
    return new OrganizationApi(helper);
  }

  public ProjectApi project() {
    return new ProjectApi(helper);
  }

  public IssueApi issue() {
    return new IssueApi(helper);
  }

  public SourceApi source() {
    return new SourceApi(helper);
  }

  public SettingsApi settings() {
    return new SettingsApi(helper);
  }

  public QualityProfileApi qualityProfile() {
    return new QualityProfileApi(helper);
  }

  public PluginsApi plugins() {
    return new PluginsApi(helper);
  }

  public RulesApi rules() {
    return new RulesApi(helper);
  }

  public SystemApi system() {
    return new SystemApi(helper);
  }
}
