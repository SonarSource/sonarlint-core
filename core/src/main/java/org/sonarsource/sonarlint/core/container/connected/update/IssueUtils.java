/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonar.scanner.protocol.input.ScannerInput;

public class IssueUtils {
  private IssueUtils() {
    // utility class, forbidden constructor
  }

  public static Map<String, Iterable<ScannerInput.ServerIssue>> groupByFileKey(Iterable<ScannerInput.ServerIssue> issues) {
    Map<String, Iterable<ScannerInput.ServerIssue>> groupedIssues = new HashMap<>();
    for (ScannerInput.ServerIssue issue : issues) {
      String fileKey = createFileKey(issue);
      Iterable<ScannerInput.ServerIssue> serverIssues = groupedIssues.get(fileKey);
      if (serverIssues == null) {
        serverIssues = new ArrayList<>();
        groupedIssues.put(fileKey, serverIssues);
      }
      ((List<ScannerInput.ServerIssue>) serverIssues).add(issue);
    }
    return groupedIssues;
  }

  public static String createFileKey(ScannerInput.ServerIssue issue) {
    return issue.getModuleKey() + ":" + issue.getPath();
  }
}
