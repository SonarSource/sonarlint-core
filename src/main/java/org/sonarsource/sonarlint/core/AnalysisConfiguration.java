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
package org.sonarsource.sonarlint.core;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

public class AnalysisConfiguration {

  private final Iterable<InputFile> inputFiles;
  private final Map<String, String> extraProperties;
  private final Path workDir;
  private final Path baseDir;

  public AnalysisConfiguration(Path baseDir, Path workDir, Iterable<InputFile> inputFiles, Map<String, String> extraProperties) {
    this.baseDir = baseDir;
    this.workDir = workDir;
    this.inputFiles = inputFiles;
    this.extraProperties = extraProperties;
  }

  public interface InputFile {

    Path path();

    /**
     * Flag an input file as test file. Analyzers may apply different rules on test files.
     */
    @CheckForNull
    boolean isTest();

    /**
     * Charset to be used to read file content. If null it means the charset is unknown and analysis will likely use JVM default encoding to read the file.
     */
    @CheckForNull
    Charset charset();

  }

  public Map<String, String> extraProperties() {
    return extraProperties;
  }

  public Path baseDir() {
    return baseDir;
  }

  /**
   * Work dir specific for this analysis.
   */
  public Path workDir() {
    return workDir;
  }

  public Iterable<InputFile> inputFiles() {
    return inputFiles;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

}
