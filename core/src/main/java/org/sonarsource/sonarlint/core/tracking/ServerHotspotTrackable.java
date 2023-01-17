/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.tracking;

import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.issuetracking.Trackable;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

public class ServerHotspotTrackable implements Trackable {

  private final ServerHotspot serverHotspot;

  public ServerHotspotTrackable(ServerHotspot serverHotspot) {
    this.serverHotspot = serverHotspot;
  }

  @Override
  public Object getClientObject() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRuleKey() {
    return serverHotspot.getRuleKey();
  }

  @Override
  public IssueSeverity getSeverity() {
    // no severity on hotspots
    return null;
  }

  @Override
  public RuleType getType() {
    return RuleType.SECURITY_HOTSPOT;
  }

  @Override
  public String getMessage() {
    return serverHotspot.getMessage();
  }

  @Override
  public Integer getLine() {
    return serverHotspot.getTextRange().getStartLine();
  }

  @Override
  public String getLineHash() {
    // no line hash for hotspots
    return null;
  }

  @Override
  public TextRangeWithHash getTextRange() {
    // no text range with hash for hotspots
    return null;
  }

  @Override
  public Long getCreationDate() {
    return serverHotspot.getCreationDate().toEpochMilli();
  }

  @Override
  public String getServerIssueKey() {
    return serverHotspot.getKey();
  }

  @Override
  public boolean isResolved() {
    return serverHotspot.isResolved();
  }
}
