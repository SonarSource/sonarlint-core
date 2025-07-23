/*
 * SonarLint Core - Server API
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverapi.sca;

import java.util.List;
import java.util.UUID;

public record GetIssuesReleasesResponse(List<IssuesRelease> issuesReleases, Page page) {
  public record IssuesRelease(UUID key, Type type, Severity severity, Status status, Release release, List<Transition> transitions) {
    public record Release(String packageName, String version) {
    }

    public enum Severity {
      INFO, LOW, MEDIUM, HIGH, BLOCKER
    }

    public enum Type {
      VULNERABILITY, PROHIBITED_LICENSE
    }

    public enum Status {
      OPEN, CONFIRM, ACCEPT, SAFE, FIXED
    }

    public enum Transition {
      CONFIRM, REOPEN, SAFE, FIXED, ACCEPT
    }
  }

  public record Page(int total) {
  }
}
