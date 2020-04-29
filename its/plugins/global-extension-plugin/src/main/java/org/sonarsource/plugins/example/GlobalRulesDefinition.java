/*
 * Example Plugin with global extension
 * Copyright (C) 2009-2020 SonarSource SA
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

import org.sonar.api.server.rule.RuleParamType;
import org.sonar.api.server.rule.RulesDefinition;

public final class GlobalRulesDefinition implements RulesDefinition {

  static final String RULE_KEY = "inc";
  static final String KEY = "global";
  static final String NAME = "Global";

  @Override
  public void define(Context context) {
    NewRepository repository = context.createRepository(KEY, GlobalLanguage.LANGUAGE_KEY).setName(NAME);
    NewRule rule = repository.createRule(RULE_KEY)
      .setActivatedByDefault(true)
      .setName("Increment")
      .setHtmlDescription("Increment message after every analysis");
    rule.createParam("stringParam")
      .setType(RuleParamType.STRING)
      .setDefaultValue("string parameter")
      .setDescription("A string parameter");
    rule.createParam("textParam")
      .setType(RuleParamType.TEXT)
      .setDefaultValue("text\nparameter")
      .setDescription("A text parameter");
    rule.createParam("intParam")
      .setType(RuleParamType.INTEGER)
      .setDefaultValue("42")
      .setDescription("An int parameter");
    rule.createParam("boolParam")
      .setType(RuleParamType.BOOLEAN)
      .setDefaultValue("true")
      .setDescription("A boolean parameter");
    rule.createParam("floatParam")
      .setType(RuleParamType.FLOAT)
      .setDefaultValue("3.14159265358")
      .setDescription("A float parameter");
    rule.createParam("enumParam")
      .setType(RuleParamType.singleListOfValues("enum1", "enum2", "enum3"))
      .setDefaultValue("enum1")
      .setDescription("An enum parameter");
    rule.createParam("enumListParam")
      .setType(RuleParamType.multipleListOfValues("list1", "list2", "list3"))
      .setDefaultValue("list1,list2")
      .setDescription("An enum list parameter");

    repository.done();
  }

}
