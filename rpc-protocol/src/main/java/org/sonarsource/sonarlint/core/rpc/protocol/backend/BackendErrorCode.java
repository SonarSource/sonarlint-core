/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend;

public class BackendErrorCode {

  public static final int CONNECTION_NOT_FOUND = -1;
  public static final int CONFIG_SCOPE_NOT_FOUND = -2;
  public static final int RULE_NOT_FOUND = -3;
  public static final int BACKEND_ALREADY_INITIALIZED = -4;
  public static final int ISSUE_NOT_FOUND = -5;
  public static final int CONFIG_SCOPE_NOT_BOUND = -6;
  public static final int HTTP_REQUEST_TIMEOUT = -7;
  public static final int HTTP_REQUEST_FAILED = -8;
  public static final int TASK_EXECUTION_TIMEOUT = -9;
}
