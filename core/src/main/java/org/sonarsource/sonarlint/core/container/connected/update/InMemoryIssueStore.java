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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sonar.scanner.protocol.input.ScannerInput;

// TODO for testing only. Move to /test/ at the soonest
public class InMemoryIssueStore implements IssueStore {

  private Map<String, List<ScannerInput.ServerIssue>> issues;

  @Override
  public void save(Map<String, List<ScannerInput.ServerIssue>> issues) {
    this.issues = issues;
  }

  @Override
  public List<ScannerInput.ServerIssue> load(String fileKey) {
    return issues.getOrDefault(fileKey, Collections.emptyList());
  }
}
