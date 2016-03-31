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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.sonar.api.rule.RuleKey;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;

import static org.apache.commons.lang3.StringEscapeUtils.escapeJson;

public class Main {

  private static final Map<String, String> languages = ImmutableMap.of("java", "Java", "js", "JavaScript", "php", "PHP");

  public static void main(String[] args) throws MalformedURLException {

    String version = args[0];

    List<Path> pluginPaths = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      pluginPaths.add(Paths.get(args[i]));
    }

    StandaloneGlobalConfiguration.Builder builder = StandaloneGlobalConfiguration.builder()
      .setLogOutput(new LogOutput() {
        @Override
        public void log(String formattedMessage, Level level) {
          // Ignore
        }
      });

    for (Path path : pluginPaths) {
      builder.addPlugin(path.toUri().toURL());
    }
    StandaloneSonarLintEngineImpl client = new StandaloneSonarLintEngineImpl(builder.build());
    client.start();

    Table<String, String, RuleDetails> rulesByKeyAndLanguage = TreeBasedTable.create();
    for (String ruleKeyStr : ((StandaloneGlobalContainer) client.getGlobalContainer()).getActiveRuleKeys()) {
      RuleDetails ruleDetails = client.getRuleDetails(ruleKeyStr);
      RuleKey ruleKey = RuleKey.parse(ruleKeyStr);
      rulesByKeyAndLanguage.put(ruleKey.rule(), ruleDetails.getLanguage(), ruleDetails);
    }

    try {
      System.out.print("{");
      System.out.print("\"version\": \"");
      System.out.print(version);
      System.out.print("\",");

      System.out.print("\"rules\": [");
      boolean first = true;
      for (String ruleKey : rulesByKeyAndLanguage.rowKeySet()) {
        if (!first) {
          System.out.print(",");
        }
        first = false;
        System.out.print("{");
        System.out.print("\"key\": \"");
        System.out.print(ruleKey);
        System.out.print("\",");
        System.out.print("\"title\": \"");
        System.out.print(escapeJson(rulesByKeyAndLanguage.row(ruleKey).values().iterator().next().getName()));
        System.out.print("\",");

        Set<String> mergedTags = new HashSet<>();
        for (RuleDetails rule : rulesByKeyAndLanguage.row(ruleKey).values()) {
          mergedTags.addAll(Arrays.asList(rule.getTags()));
        }
        writeTags(mergedTags);
        System.out.print(",");

        System.out.print("\"implementations\": [");

        boolean firstLang = true;
        for (Map.Entry<String, RuleDetails> detailPerLanguage : rulesByKeyAndLanguage.row(ruleKey).entrySet()) {
          if (!firstLang) {
            System.out.print(",");
          }
          firstLang = false;
          RuleDetails ruleDetails = detailPerLanguage.getValue();
          System.out.print("{");
          System.out.print("\"key\": \"");
          System.out.print(ruleDetails.getKey());
          System.out.print("\",");
          System.out.print("\"language\": \"");
          System.out.print(languageLabel(detailPerLanguage.getKey()));
          System.out.print("\",");
          System.out.print("\"title\": \"");
          System.out.print(escapeJson(ruleDetails.getName()));
          System.out.print("\",");
          System.out.print("\"description\": \"");
          System.out.print(escapeJson(ruleDetails.getHtmlDescription()));
          System.out.print("\",");
          System.out.print("\"severity\": \"");
          System.out.print(StringUtils.capitalize(ruleDetails.getSeverity().toLowerCase()));
          System.out.print("\",");
          String[] tags = ruleDetails.getTags();
          writeTags(Arrays.asList(tags));
          System.out.print("}");
        }
        System.out.print("]");
        System.out.print("}");
      }
      System.out.print("]");
      System.out.print("}");
    } finally {
      client.stop();
    }

  }

  private static String languageLabel(String languageKey) {
    return languages.containsKey(languageKey) ? languages.get(languageKey) : languageKey;
  }

  private static void writeTags(Iterable<String> tags) {
    System.out.print("\"tags\": [");
    boolean firstTag = true;
    for (String tag : tags) {
      if (!firstTag) {
        System.out.print(",");
      }
      firstTag = false;
      System.out.print("\"" + escapeJson(tag) + "\"");
    }
    System.out.print("]");
  }

}
