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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

public enum FeatureFlag {
  SHOULD_MANAGE_SMART_NOTIFICATIONS,
  TAINT_VULNERABILITIES_ENABLED,
  SHOULD_SYNCHRONIZE_PROJECTS,
  SHOULD_MANAGE_LOCAL_SERVER,
  ENABLE_SECURITY_HOTSPOTS,
  SHOULD_MANAGE_SERVER_SENT_EVENTS,
  ENABLE_DATAFLOW_BUG_DETECTION,
  SHOULD_MANAGE_FULL_SYNCHRONIZATION,
  ENABLE_TELEMETRY,
  CAN_OPEN_FIX_SUGGESTION,
  ENABLE_MONITORING
}
