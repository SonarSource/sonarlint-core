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

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.sonarsource.sonarlint.core.SonarLintClientImpl;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.GlobalConfiguration.Builder;
import org.sonarsource.sonarlint.core.client.api.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.SonarLintClient;

import static org.apache.commons.lang3.StringEscapeUtils.escapeJson;

public class Main {

  public static void main(String[] args) throws MalformedURLException {

    List<Path> pluginPaths = new ArrayList<>();
    for (String arg : args) {
      pluginPaths.add(Paths.get(arg));
    }
    SonarLintClient client = new SonarLintClientImpl();
    Builder builder = GlobalConfiguration.builder();
    for (Path path : pluginPaths) {
      builder.addPlugin(path.toUri().toURL());
    }
    client.start(builder.build());

    try {
      System.out.print("[");
      boolean first = true;
      for (String ruleKey : client.getActiveRuleKeys()) {
        if (!first) {
          System.out.print(",");
        }
        first = false;
        RuleDetails ruleDetails = client.getRuleDetails(ruleKey);
        System.out.print("{");
        System.out.print("\"Key\": \"");
        System.out.print(ruleKey);
        System.out.print("\",");
        System.out.print("\"Data\": {");
        System.out.print("\"" + ruleDetails.getLanguage() + "\": {");
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
        System.out.print("}");
        System.out.print("}");
      }
      System.out.print("]");
    } finally {
      client.stop();
    }

  }

}
