/*
 * SonarLint Core - Slf4j log adaptor
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.slf4j;

import java.util.Map;

/**
 * Some libraries uses this static method, so we have to provide a fake implementation.
 * For example https://github.com/zeroturnaround/zt-exec/blob/933f571414836889e7162152293c595b49baa8ae/src/main/java/org/zeroturnaround/exec/ProcessExecutor.java#L1160
 *
 */
public class MDC {

  public static Map<String, String> getCopyOfContextMap() {
    return null;
  }

}
