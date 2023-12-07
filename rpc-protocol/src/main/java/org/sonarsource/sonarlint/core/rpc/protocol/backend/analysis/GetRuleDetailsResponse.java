/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis;

import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class GetRuleDetailsResponse {

  private final IssueSeverity severity;
  private final RuleType type;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final List<ImpactDto> defaultImpacts;
  private final VulnerabilityProbability vulnerabilityProbability;


  public GetRuleDetailsResponse(IssueSeverity severity, RuleType type, CleanCodeAttribute cleanCodeAttribute,
    List<ImpactDto> defaultImpacts, @Nullable VulnerabilityProbability vulnerabilityProbability) {
    this.severity = severity;
    this.type = type;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.defaultImpacts = defaultImpacts;
    this.vulnerabilityProbability = vulnerabilityProbability;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public List<ImpactDto> getDefaultImpacts() {
    return defaultImpacts;
  }

  @CheckForNull
  public VulnerabilityProbability getVulnerabilityProbability() {
    return vulnerabilityProbability;
  }
}
