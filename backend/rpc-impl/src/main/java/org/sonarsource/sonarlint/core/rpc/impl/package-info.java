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
<<<<<<<< HEAD:backend/rpc-impl/src/main/java/org/sonarsource/sonarlint/core/rpc/impl/package-info.java
@ParametersAreNonnullByDefault
package org.sonarsource.sonarlint.core.rpc.impl;

import javax.annotation.ParametersAreNonnullByDefault;

========
package testutils;

import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class NoopCancelChecker implements CancelChecker {
  @Override
  public void checkCanceled() {
    // do nothing
  }
}
>>>>>>>> 3f040ee2a (Rework the use of completable futures):core/src/test/java/testutils/NoopCancelChecker.java
