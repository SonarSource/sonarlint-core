/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.rules;

import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.rule.extractor.SonarLintRuleDefinition;

public class RulesFixtures {
  public static SonarLintRuleDefinition aRule() {
    RulesDefinition.Context c = new RulesDefinition.Context();
    var repository = c.createRepository("repo", Language.JAVA.getLanguageKey());
    repository.createRule("ruleKey")
      .setName("ruleName")
      .setType(RuleType.BUG)
      .setHtmlDescription("Hello, world!")
        .createParam("paramKey")
          .setName("paramName")
          .setType(RuleParamType.TEXT)
          .setDescription("paramDesc")
          .setDefaultValue("defaultValue");
    repository.done();
    var rule = c.repositories().get(0).rule("ruleKey");
    return new SonarLintRuleDefinition(rule);
  }
}
