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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultInputDir;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.MessageException;
import org.sonarsource.sonarlint.core.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.util.ProgressReport;

/**
 * Index input files into {@link InputPathCache}.
 */
@BatchSide
public class FileIndexer {

  private final InputFileBuilderFactory inputFileBuilderFactory;
  private final AnalysisConfiguration analysisConfiguration;

  private ProgressReport progressReport;
  private List<Future<Void>> tasks;

  public FileIndexer(InputFileBuilderFactory inputFileBuilderFactory, AnalysisConfiguration analysisConfiguration) {
    this.inputFileBuilderFactory = inputFileBuilderFactory;
    this.analysisConfiguration = analysisConfiguration;
  }

  void index(DefaultModuleFileSystem fileSystem) {
    progressReport = new ProgressReport("Report about progress of file indexation", TimeUnit.SECONDS.toMillis(10));
    progressReport.start("Index files");

    Progress progress = new Progress();

    InputFileBuilder inputFileBuilder = inputFileBuilderFactory.create(fileSystem);
    indexFiles(fileSystem, progress, inputFileBuilder, analysisConfiguration.inputFiles());

    progressReport.stop(progress.count() + " files indexed");

  }

  private void indexFiles(DefaultModuleFileSystem fileSystem, Progress progress, InputFileBuilder inputFileBuilder, Iterable<AnalysisConfiguration.InputFile> inputFiles) {
    for (AnalysisConfiguration.InputFile file : inputFiles) {
      indexFile(inputFileBuilder, fileSystem, progress, file);
    }
  }

  private void indexFile(InputFileBuilder inputFileBuilder, DefaultModuleFileSystem fileSystem, Progress progress, AnalysisConfiguration.InputFile file) {
    DefaultInputFile inputFile = inputFileBuilder.create(file.path().toFile());
    if (inputFile != null) {
      // Set basedir on input file prior to adding it to the FS since exclusions filters may require the absolute path
      inputFile.setModuleBaseDir(fileSystem.baseDirPath());
      indexFile(inputFileBuilder, fileSystem, progress, inputFile, file.isTest() ? Type.TEST : Type.MAIN);
    }
  }

  private void indexFile(final InputFileBuilder inputFileBuilder, final DefaultModuleFileSystem fs, final Progress status, final DefaultInputFile inputFile,
    final InputFile.Type type) {

    DefaultInputFile completedInputFile = inputFileBuilder.completeAndComputeMetadata(inputFile, type);
    if (completedInputFile != null) {
      fs.add(completedInputFile);
      status.markAsIndexed(completedInputFile);
      File parentDir = completedInputFile.file().getParentFile();
      String relativePath = new PathResolver().relativePath(fs.baseDir(), parentDir);
      if (relativePath != null) {
        DefaultInputDir inputDir = new DefaultInputDir(fs.moduleKey(), relativePath);
        fs.add(inputDir);
      }
    }

  }

  private class Progress {
    private final Set<Path> indexed = new HashSet<>();

    synchronized void markAsIndexed(InputFile inputFile) {
      if (indexed.contains(inputFile.path())) {
        throw MessageException.of("File " + inputFile + " can't be indexed twice. Please check that inclusion/exclusion patterns produce "
          + "disjoint sets for main and test files");
      }
      indexed.add(inputFile.path());
      progressReport.message(indexed.size() + " files indexed...  (last one was " + inputFile.relativePath() + ")");
    }

    int count() {
      return indexed.size();
    }
  }

}
