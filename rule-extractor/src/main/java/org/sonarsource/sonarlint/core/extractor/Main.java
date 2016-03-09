/*
 * SonarLint Core - Rule Extractor Utility
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.extractor;

import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.SonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;

import static org.apache.commons.lang3.StringEscapeUtils.escapeJson;

public class Main {

  public static void main(String[] args) throws MalformedURLException {

    List<Path> pluginPaths = new ArrayList<>();
    for (String arg : args) {
      pluginPaths.add(Paths.get(arg));
    }
    Builder builder = GlobalConfiguration.builder();
    for (Path path : pluginPaths) {
      builder.addPlugin(path.toUri().toURL());
    }
    SonarLintEngineImpl client = new SonarLintEngineImpl(builder.build());
    client.start();

    Table<String, String, RuleDetails> rulesByKeyAndLanguage = TreeBasedTable.create();
    for (String ruleKeyStr : ((StandaloneGlobalContainer) client.getGlobalContainer()).getActiveRuleKeys()) {
      RuleDetails ruleDetails = client.getRuleDetails(ruleKeyStr);
      RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
      rulesByKeyAndLanguage.put(ruleKey.rule(), ruleDetails.getLanguage(), ruleDetails);
    }

    try {
      System.out.print("[");
      boolean first = true;
      for (String ruleKey : rulesByKeyAndLanguage.rowKeySet()) {
        if (!first) {
          System.out.print(",");
        }
        first = false;
        System.out.print("{");
        System.out.print("\"Key\": \"");
        System.out.print(ruleKey);
        System.out.print("\",");
        System.out.print("\"Data\": {");

        boolean firstLang = true;
        for (Map.Entry<String, RuleDetails> detailPerLanguage : rulesByKeyAndLanguage.row(ruleKey).entrySet()) {
          if (!firstLang) {
            System.out.print(",");
          }
          firstLang = false;
          RuleDetails ruleDetails = detailPerLanguage.getValue();
          System.out.print("\"" + detailPerLanguage.getKey() + "\": {");
          System.out.print("\"Title\": \"");
          System.out.print(escapeJson(ruleDetails.getName()));
          System.out.print("\",");
          System.out.print("\"Description\": \"");
          System.out.print(escapeJson(ruleDetails.getHtmlDescription()));
          System.out.print("\",");
          System.out.print("\"Severity\": \"");
          System.out.print(StringUtils.capitalize(ruleDetails.getSeverity().toLowerCase()));
          System.out.print("\",");
          System.out.print("\"Tags\": [");
          boolean firstTag = true;
          for (String tag : ruleDetails.getTags()) {
            if (!firstTag) {
              System.out.print(",");
            }
            firstTag = false;
            System.out.print("\"" + escapeJson(tag) + "\"");
          }
          System.out.print("]");
          System.out.print("}");
        }
        System.out.print("}");
        System.out.print("}");
      }
      System.out.print("]");
    } finally {
      client.stop();
    }

  }

}
