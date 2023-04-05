/*
 * SonarLint Core - Plugin Commons
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
package org.sonarsource.sonarlint.core.plugin.commons.container;

import org.sonar.api.Startable;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.lang.Nullable;

public class StartableBeanPostProcessor implements DestructionAwareBeanPostProcessor {
  @Override
  @Nullable
  public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof Startable) {
      ((Startable) bean).start();
    }
    return bean;
  }

  @Override
  public boolean requiresDestruction(Object bean) {
    return bean instanceof Startable;
  }

  @Override
  public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
    try {
      // note: Spring will call close() on AutoCloseable beans.
      if (bean instanceof Startable) {
        ((Startable) bean).stop();
      }
    } catch (Exception e) {
      SonarLintLogger.get()
        .warn("Dispose of component {} failed", bean.getClass().getCanonicalName(), e);
    }
  }
}
