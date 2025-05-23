/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.io.ByteArrayInputStream;
import jetbrains.exodus.bindings.BindingUtils;
import jetbrains.exodus.bindings.ComparableBinding;
import jetbrains.exodus.util.LightOutputStream;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;

public class HotspotReviewStatusBinding extends ComparableBinding {

  @Override
  public Comparable readObject(@NotNull ByteArrayInputStream stream) {
    return HotspotReviewStatus.values()[BindingUtils.readInt(stream)];
  }

  @Override
  public void writeObject(@NotNull LightOutputStream output, @NotNull Comparable object) {
    final HotspotReviewStatus cPair = (HotspotReviewStatus) object;
    output.writeUnsignedInt(cPair.ordinal() ^ 0x80_000_000);
  }

}
