/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import org.junit.jupiter.api.Test;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.server.rule.RulesDefinition.Rule;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintRuleDefinitionTests {

  @Test
  void convertMarkdownDescriptionToHtml() {
    RulesDefinition.Context context = new RulesDefinition.Context();
    NewRepository newRepository = context.createRepository("my-repo", "java");
    NewRule createRule = newRepository.createRule("my-rule-with-markdown-description")
      .setName("My Rule");
    createRule.setMarkdownDescription("  = Title\n  * one\n* two");
    newRepository.done();

    Rule rule = context.repositories().get(0).rule("my-rule-with-markdown-description");

    SonarLintRuleDefinition underTest = new SonarLintRuleDefinition(rule);

    assertThat(underTest.getHtmlDescription()).isEqualTo("<h1>Title</h1><ul><li>one</li>\n"
      + "<li>two</li></ul>");
  }

}
