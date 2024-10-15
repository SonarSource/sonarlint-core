/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons;

import java.util.function.BiPredicate;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class CSharpSupport {

  private CSharpSupport() {
    // Only static utils
  }

  static BiPredicate<String, Object> maybePatchExtensionFilter(String pluginKey, BiPredicate<String, Object> maybeWrapped) {
    return isCsharpPlugin(pluginKey) ?
      patchExtensionFilter(maybeWrapped) : maybeWrapped;
  }

  static boolean isCsharpPlugin(String pluginKey) {
    return SonarLanguage.CS.getPluginKey().equals(pluginKey);
  }

  private static BiPredicate<String, Object> patchExtensionFilter(BiPredicate<String, Object> wrapped) {
    return (pluginKey, object) -> isRulesDefinitions(object)
      && wrapped.test(pluginKey, object);
  }

  private static boolean isRulesDefinitions(Object object) {
    return ExtensionUtils.isType(object, RulesDefinition.class);
  }
}
