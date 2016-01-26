/*
 * SonarLint Core Library
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.io.Serializable;
import java.util.Collection;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.SonarIndex;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.sensor.internal.SensorStorage;
import org.sonar.api.config.Settings;
import org.sonar.api.design.Dependency;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.MeasuresFilter;
import org.sonar.api.measures.Metric;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.File;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;

public class LtsApiSensorContext extends DefaultSensorContext implements SensorContext {

  private final SonarIndex index;
  private final Project project;

  public LtsApiSensorContext(SonarIndex index, Project project, Settings settings, FileSystem fs, ActiveRules activeRules, SensorStorage sensorStorage) {
    super(settings, fs, activeRules, sensorStorage);
    this.index = index;
    this.project = project;

  }

  public Project getProject() {
    return project;
  }

  @Override
  public boolean index(Resource resource) {
    throw unsupported();
  }

  private UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Unsupported in SonarLint");
  }

  @Override
  public boolean index(Resource resource, Resource parentReference) {
    throw unsupported();
  }

  @Override
  public boolean isExcluded(Resource reference) {
    throw unsupported();
  }

  @Override
  public boolean isIndexed(Resource reference, boolean acceptExcluded) {
    throw unsupported();
  }

  @Override
  public Resource getParent(Resource reference) {
    throw unsupported();
  }

  @Override
  public Collection<Resource> getChildren(Resource reference) {
    throw unsupported();
  }

  @Override
  public <G extends Serializable> Measure<G> getMeasure(Metric<G> metric) {
    throw unsupported();
  }

  @Override
  public <M> M getMeasures(MeasuresFilter<M> filter) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(Measure measure) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(Metric metric, Double value) {
    throw unsupported();
  }

  @Override
  public <G extends Serializable> Measure<G> getMeasure(Resource resource, Metric<G> metric) {
    throw unsupported();
  }

  @Override
  public String saveResource(Resource resource) {
    throw unsupported();
  }

  public boolean saveResource(Resource resource, Resource parentReference) {
    throw unsupported();
  }

  @Override
  public Resource getResource(Resource resource) {
    return index.getResource(resource);
  }

  @Override
  public <M> M getMeasures(Resource resource, MeasuresFilter<M> filter) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(Resource resource, Metric metric, Double value) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(Resource resource, Measure measure) {
    throw unsupported();
  }

  @Override
  public Dependency saveDependency(Dependency dependency) {
    throw unsupported();
  }

  @Override
  public void saveSource(Resource reference, String source) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(InputFile inputFile, Metric metric, Double value) {
    return null;
  }

  @Override
  public Measure saveMeasure(InputFile inputFile, Measure measure) {
    return null;
  }

  @Override
  public Resource getResource(InputPath inputPath) {
    Resource r;
    if (inputPath instanceof InputDir) {
      r = Directory.create(((InputDir) inputPath).relativePath());
    } else if (inputPath instanceof InputFile) {
      r = File.create(((InputFile) inputPath).relativePath());
    } else {
      throw new IllegalArgumentException("Unknow input path type: " + inputPath);
    }
    return getResource(r);
  }
}
