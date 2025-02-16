/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

public class AiCodeFixFixtures {
  private AiCodeFixFixtures() {
    // utility class
  }

  public static class Builder {
    private Set<String> supportedRules = Set.of();
    private boolean organizationEligible = true;
    private AiCodeFixFeatureEnablement enablement = AiCodeFixFeatureEnablement.DISABLED;
    private List<String> enabledProjectKeys = List.of();

    public Builder withSupportedRules(Set<String> supportedRules) {
      this.supportedRules = supportedRules;
      return this;
    }

    public Builder organizationEligible(boolean organizationEligible) {
      this.organizationEligible = organizationEligible;
      return this;
    }

    public Builder disabled() {
      this.enablement = AiCodeFixFeatureEnablement.DISABLED;
      return this;
    }

    public Builder enabledForProjects(String projectKey) {
      this.enablement = AiCodeFixFeatureEnablement.ENABLED_FOR_SOME_PROJECTS;
      this.enabledProjectKeys = List.of(projectKey);
      return this;
    }

    public Builder enabledForAllProjects() {
      this.enablement = AiCodeFixFeatureEnablement.ENABLED_FOR_ALL_PROJECTS;
      return this;
    }

    public void create(Path path) {
      var settings = Sonarlint.AiCodeFixSettings.newBuilder()
        .addAllSupportedRules(supportedRules)
        .setOrganizationEligible(organizationEligible)
        .setEnablement(Sonarlint.AiCodeFixEnablement.valueOf(enablement.name()))
        .addAllEnabledProjectKeys(enabledProjectKeys)
        .build();
      ProtobufFileUtil.writeToFile(settings, path.resolve("ai_codefix.pb"));
    }
  }
}
