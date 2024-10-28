/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;

public class StoredServerInfo {

  private final Version version;
  private final ModeDetails isMQRMode;

  public StoredServerInfo(Version version, @Nullable Boolean mode) {
    this.version = version;
    if (mode == null) {
      this.isMQRMode = ModeDetails.DEFAULT;
    } else if (mode) {
      this.isMQRMode = ModeDetails.MQR;
    } else {
      this.isMQRMode = ModeDetails.STANDARD;
    }
  }

  public Version getVersion() {
    return version;
  }

  public ModeDetails isMQRMode() {
    return this.isMQRMode;
  }

  public enum ModeDetails {
    DEFAULT, STANDARD, MQR
  }

}
