/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.time.Instant;
import org.sonarsource.sonarlint.core.serverconnection.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.FileLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.LineLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;

public class ServerIssueFixtures {
  public static LineLevelServerIssue aBatchServerIssue() {
    return new LineLevelServerIssue(
      "key",
      true,
      "repo:key",
      "message",
      "hash",
      "file/path",
      Instant.now(),
      "MINOR",
      "BUG",
      1);
  }

  public static FileLevelServerIssue aFileLevelServerIssue() {
    return new FileLevelServerIssue(
      "key",
      true,
      "repo:key",
      "message",
      "file/path",
      Instant.now(),
      "MINOR",
      "BUG");
  }

  public static RangeLevelServerIssue aServerIssue() {
    return new RangeLevelServerIssue(
      "key",
      true,
      "repo:key",
      "message",
      "hash",
      "file/path",
      Instant.now(),
      "MINOR",
      "BUG",
      new RangeLevelServerIssue.TextRange(1, 2, 3, 4));
  }

  public static ServerTaintIssue aServerTaintIssue() {
    return new ServerTaintIssue(
      "key",
      true,
      "repo:key",
      "message",
      "hash",
      "file/path",
      Instant.now(),
      "MINOR",
      "BUG",
      new ServerTaintIssue.TextRange(1, 2, 3, 4));
  }
}
