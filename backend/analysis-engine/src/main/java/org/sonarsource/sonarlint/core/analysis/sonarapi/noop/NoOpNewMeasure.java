/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi.noop;

import java.io.Serializable;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.sensor.measure.NewMeasure;

public class NoOpNewMeasure<G extends Serializable> implements NewMeasure<G> {

  @Override
  public NoOpNewMeasure<G> on(InputComponent component) {
    // do nothing
    return this;
  }

  @Override
  public NoOpNewMeasure<G> forMetric(Metric<G> metric) {
    // do nothing
    return this;
  }

  @Override
  public NoOpNewMeasure<G> withValue(Serializable value) {
    // do nothing
    return this;
  }

  @Override
  public void save() {
    // do nothing
  }

}
