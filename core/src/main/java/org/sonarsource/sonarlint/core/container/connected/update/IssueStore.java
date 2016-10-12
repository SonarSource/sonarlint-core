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

import java.util.Iterator;

import org.sonar.scanner.protocol.input.ScannerInput;

public interface IssueStore {

  /**
   * Store issues per file keys.
   *
   * For filesystem-based implementations, watch out for:
   * - Too long paths
   * - Directories with too many files
   * - (Too deep paths?)
   *
   * @param issues mapping of file keys to issues
   */
  void save(Iterator<ScannerInput.ServerIssue> issues);

  /**
   * Load issues stored for specified file.
   *
   * @param fileKey the file key
   * @return issues, possibly empty
   */
  Iterator<ScannerInput.ServerIssue> load(String fileKey);
}
