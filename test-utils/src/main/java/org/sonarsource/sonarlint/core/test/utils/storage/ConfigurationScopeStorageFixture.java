/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarlint.core.commons.LocalOnlyIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LocalOnlyIssuesRepository;

public class ConfigurationScopeStorageFixture {
  public static ConfigurationScopeStorageBuilder newBuilder(String configScopeId) {
    return new ConfigurationScopeStorageBuilder(configScopeId);
  }

  public static class ConfigurationScopeStorageBuilder {
    private final List<LocalOnlyIssue> localOnlyIssues = new ArrayList<>();
    private final String configScopeId;

    public ConfigurationScopeStorageBuilder(String configScopeId) {
      this.configScopeId = configScopeId;
    }

    public ConfigurationScopeStorageBuilder withLocalOnlyIssue(LocalOnlyIssue issue) {
      localOnlyIssues.add(issue);
      return this;
    }

    public void populate(TestDatabase database) {
      var localOnlyIssuesRepository = new LocalOnlyIssuesRepository(database.dsl());
      localOnlyIssues.forEach(issue -> localOnlyIssuesRepository.storeLocalOnlyIssue(configScopeId, issue));
    }
  }

  private ConfigurationScopeStorageFixture() {
    // utility class
  }
}
