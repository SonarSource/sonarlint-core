/*
 * Example Plugin for SonarQube
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
package org.sonarsource.plugins.example;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.api.server.rule.RulesDefinitionXmlLoader;

public final class FooLintRulesDefinition implements RulesDefinition {

  private static final String PATH_TO_RULES_XML = "/example/foolint-rules.xml";

  protected static final String KEY = "foolint";
  protected static final String NAME = "FooLint";

  private final RulesDefinitionXmlLoader xmlLoader;

  public FooLintRulesDefinition(RulesDefinitionXmlLoader xmlLoader) {
    this.xmlLoader = xmlLoader;
  }

  protected String rulesDefinitionFilePath() {
    return PATH_TO_RULES_XML;
  }

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository(KEY, "java").setName(NAME);

    InputStream rulesXml = this.getClass().getResourceAsStream(rulesDefinitionFilePath());
    if (rulesXml != null) {
      xmlLoader.load(repository, rulesXml, StandardCharsets.UTF_8.name());
    }

    repository.done();
  }

}
