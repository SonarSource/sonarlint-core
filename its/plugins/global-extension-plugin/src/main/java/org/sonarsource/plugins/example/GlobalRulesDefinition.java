/*
 * Example Plugin with global extension
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
      .setName("String parameter")
      .setDescription("An example of string parameter");
    rule.createParam("textParam")
      .setType(RuleParamType.TEXT)
      .setDefaultValue("text\nparameter")
      .setName("Text parameter")
      .setDescription("An example of text parameter");
    rule.createParam("intParam")
      .setType(RuleParamType.INTEGER)
      .setDefaultValue("42")
      .setName("Int parameter")
      .setDescription("An example of int parameter");
    rule.createParam("boolParam")
      .setType(RuleParamType.BOOLEAN)
      .setDefaultValue("true")
      .setName("Boolean parameter")
      .setDescription("An example boolean parameter");
    rule.createParam("floatParam")
      .setType(RuleParamType.FLOAT)
      .setDefaultValue("3.14159265358")
      .setName("Float parameter")
      .setDescription("An example float parameter");
    rule.createParam("enumParam")
      .setType(RuleParamType.singleListOfValues("enum1", "enum2", "enum3"))
      .setDefaultValue("enum1")
      .setName("Enum parameter")
      .setDescription("An example enum parameter");
    rule.createParam("enumListParam")
      .setType(RuleParamType.multipleListOfValues("list1", "list2", "list3"))
      .setDefaultValue("list1,list2")
      .setName("Enum list parameter")
      .setDescription("An example enum list parameter");
    rule.createParam("multipleIntegersParam")
      .setType(RuleParamType.parse("INTEGER,multiple=true,values=\"80,120,160\""))
      .setName("Enum list of integers")
      .setDescription("An example enum list of integers");

    repository.done();
  }

}
