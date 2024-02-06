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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize;

import java.util.Map;
import javax.annotation.Nullable;

public class TelemetryClientConstantAttributesDto {

  private final String productKey;
  private final String productName;
  private final String productVersion;
  private final String ideVersion;
  private final Map<String, Object> additionalAttributes;

  public TelemetryClientConstantAttributesDto(String productKey, String productName, String productVersion, String ideVersion,
    @Nullable Map<String, Object> additionalAttributes) {
    this.productKey = productKey;
    this.productName = productName;
    this.productVersion = productVersion;
    this.ideVersion = ideVersion;
    this.additionalAttributes = additionalAttributes;
  }

  public String getProductKey() {
    return productKey;
  }

  public String getProductName() {
    return productName;
  }

  public String getProductVersion() {
    return productVersion;
  }

  public String getIdeVersion() {
    return ideVersion;
  }

  public Map<String, Object> getAdditionalAttributes() {
    return additionalAttributes != null ? additionalAttributes : Map.of();
  }
}
