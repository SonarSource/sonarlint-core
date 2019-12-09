/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.Map;
import org.picocontainer.injectors.ProviderAdapter;
import org.sonarsource.sonarlint.core.container.analysis.SonarLintRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class SonarLintRulesProvider extends ProviderAdapter {

  private SonarLintRules rules;

  public SonarLintRules provide(Sonarlint.Rules storageRules) {
    if (rules == null) {
      rules = new SonarLintRules();

      for (Map.Entry<String, Sonarlint.Rules.Rule> entry : storageRules.getRulesByKeyMap().entrySet()) {
        Sonarlint.Rules.Rule r = entry.getValue();
        rules.add(new StorageRuleAdapter(r));
      }

    }
    return rules;
  }

}
