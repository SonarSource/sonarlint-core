/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.protocol.HttpContext;

public class AttributeUtils {

  public static final String PARAMS_ATTRIBUTE = "params";
  public static final String ORIGIN_ATTRIBUTE = "origin";

  private AttributeUtils() { }

  /**
   * Parsed query parameters of an HTTP request.
   * Is set in {@link org.sonarsource.sonarlint.core.embedded.server.filter.ParseParamsFilter}
   */
  public static Map<String, String> getParams(HttpContext context) {
    return Optional.of(context)
      .map(c -> (Map<String, String>) c.getAttribute(PARAMS_ATTRIBUTE))
      .orElse(Collections.emptyMap());
  }

  /**
   * Value of 'Origin' header.
   * Is set in {@link org.sonarsource.sonarlint.core.embedded.server.filter.RateLimitFilter} and can not be null afterward.
   */
  public static String getOrigin(HttpContext context) {
    return (String) context.getAttribute(ORIGIN_ATTRIBUTE);
  }

}
