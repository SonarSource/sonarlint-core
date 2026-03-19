/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.telemetry.gessie.event.payload;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.telemetry.gessie.GessieConnectionInfo.ConnectionType;

/**
 * Event payload for the IDESupportedLanguageViewed Gessie event,
 * triggered when the user opens the "Supported Languages" view in the IDE.
 */
public record IDESupportedLanguageViewedPayload(
  String localUserId,
  String sqIdeVersion,
  String os,
  @Nullable ConnectionType connectionType,
  @Nullable String userUuid,
  @Nullable String organizationUuidV4,
  @Nullable String sqsInstallationId
) implements GessieEventPayload {

  public static final String EVENT_TYPE = "Analytics.IDE.IDESupportedLanguageViewed";
  public static final String EVENT_VERSION = "1";

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }

  @Override
  public String getEventVersion() {
    return EVENT_VERSION;
  }

}
