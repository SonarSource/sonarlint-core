/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry;

public enum AnalysisReportingType {
  VCS_CHANGED_ANALYSIS_TYPE("trigger_count_vcs_changed_files"),
  ALL_FILES_ANALYSIS_TYPE("trigger_count_all_project_files"),
  PRE_COMMIT_ANALYSIS_TYPE("trigger_count_pre_commit"),
  WHOLE_FOLDER_HOTSPOTS_SCAN_TYPE("trigger_count_whole_folder_hotspots_scan");

  private final String id;

  AnalysisReportingType(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
