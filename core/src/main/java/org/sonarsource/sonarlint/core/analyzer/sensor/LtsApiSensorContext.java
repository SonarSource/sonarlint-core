/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.io.Serializable;
import java.util.Collection;
import org.sonar.api.SonarRuntime;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputModule;
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
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.utils.PathUtils;
import org.sonar.api.utils.System2;

public class LtsApiSensorContext extends DefaultSensorContext implements SensorContext {

  private static final class FakeResourceForSonarLint extends Resource {
    private final InputPath inputPath;

    private FakeResourceForSonarLint(InputPath inputPath) {
      this.inputPath = inputPath;
    }

    @Override
    public String getEffectiveKey() {
      return inputPath.key();
    }

    @Override
    public String getPath() {
      return inputPath.absolutePath();
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public String getLongName() {
      return null;
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public Language getLanguage() {
      return null;
    }

    @Override
    public String getScope() {
      return null;
    }

    @Override
    public String getQualifier() {
      return null;
    }

    @Override
    public Resource getParent() {
      return null;
    }

    @Override
    public boolean matchFilePattern(String antPattern) {
      return false;
    }
  }

  private final Project project;

  public LtsApiSensorContext(Project project, InputModule inputModule, Settings settings, FileSystem fs, ActiveRules activeRules, SensorStorage sensorStorage,
    SonarRuntime sqRuntime) {
    super(inputModule, settings, fs, activeRules, sensorStorage, sqRuntime);
    this.project = project;

  }

  public Project getProject() {
    return project;
  }

  private static UnsupportedOperationException unsupported() {
    return new UnsupportedOperationException("Unsupported in SonarLint");
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
    return null;
  }

  @Override
  public Measure saveMeasure(Metric metric, Double value) {
    return null;
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
    // Here we should expect that resource key is the absolute path thanks to SonarLintInputFile::relativePath()
    String absolutePath = resource.getKey();
    if (!System2.INSTANCE.isOsWindows() && !absolutePath.startsWith("/")) {
      // Can occurs on Linux because File.create(relativePath) will remove leading /
      absolutePath = "/" + resource.getKey();
    }
    resource
      .setEffectiveKey(absolutePath)
      .setPath(absolutePath)
      .setKey(absolutePath);
    return resource;
  }

  @Override
  public <M> M getMeasures(Resource resource, MeasuresFilter<M> filter) {
    throw unsupported();
  }

  @Override
  public Measure saveMeasure(Resource resource, Metric metric, Double value) {
    return null;
  }

  @Override
  public Measure saveMeasure(Resource resource, Measure measure) {
    return null;
  }

  @Override
  public Dependency saveDependency(Dependency dependency) {
    return null;
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
  public Resource getResource(final InputPath inputPath) {
    if (inputPath.isFile()) {
      File f = new File() {

        @Override
        public String getEffectiveKey() {
          return inputPath.key();
        }

        @Override
        public String getPath() {
          return inputPath.absolutePath();
        }

        @Override
        public org.sonar.api.resources.Directory getParent() {
          return Directory.create(PathUtils.sanitize(inputPath.path().getParent().toString()));
        }
      };
      f.setKey(inputPath.key());
      return f;
    } else {
      return new FakeResourceForSonarLint(inputPath);
    }
  }
}
