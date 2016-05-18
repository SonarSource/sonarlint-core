/*
 * SonarLint Core - Implementation
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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.scan.filesystem.FileQuery;
import org.sonar.api.scan.filesystem.ModuleFileSystem;

import javax.annotation.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class AdapterModuleFileSystem implements ModuleFileSystem {
  private SonarLintFileSystem sonarLintFileSystem;

  public AdapterModuleFileSystem(SonarLintFileSystem sonarLintFileSystem) {
    this.sonarLintFileSystem = sonarLintFileSystem;
  }

  @Override
  public File baseDir() {
    throw new UnsupportedOperationException();
  }

  @Override
  public File buildDir() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<File> sourceDirs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<File> testDirs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<File> binaryDirs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<File> files(FileQuery query) {
    Collection<FilePredicate> predicates = Lists.newArrayList();
    for (Map.Entry<String, Collection<String>> entry : query.attributes().entrySet()) {
      predicates.add(fromDeprecatedAttribute(entry.getKey(), entry.getValue()));
    }
    return ImmutableList.copyOf(sonarLintFileSystem.files(predicates().and(predicates)));
  }

  private FilePredicate fromDeprecatedAttribute(String key, Collection<String> value) {
    if ("TYPE".equals(key)) {
      return predicates().or(Collections2.transform(value, new Function<String, FilePredicate>() {
        @Override
        public FilePredicate apply(@Nullable String s) {
          return s == null ? predicates().all() : predicates().hasType(org.sonar.api.batch.fs.InputFile.Type.valueOf(s));
        }
      }));
    }
    if ("STATUS".equals(key)) {
      return predicates().or(Collections2.transform(value, new Function<String, FilePredicate>() {
        @Override
        public FilePredicate apply(@Nullable String s) {
          return s == null ? predicates().all() : predicates().hasStatus(org.sonar.api.batch.fs.InputFile.Status.valueOf(s));
        }
      }));
    }
    if ("LANG".equals(key)) {
      return predicates().or(Collections2.transform(value, new Function<String, FilePredicate>() {
        @Override
        public FilePredicate apply(@Nullable String s) {
          return s == null ? predicates().all() : predicates().hasLanguage(s);
        }
      }));
    }
    if ("CMP_KEY".equals(key)) {
      return predicates().or(Collections2.transform(value, new Function<String, FilePredicate>() {
        @Override
        public FilePredicate apply(@Nullable String s) {
          return s == null ? predicates().all() : new AdditionalFilePredicates.KeyPredicate(s);
        }
      }));
    }
    throw new IllegalArgumentException("Unsupported file attribute: " + key);
  }

  private FilePredicates predicates() {
    return sonarLintFileSystem.predicates();
  }

  @Override
  public Charset sourceCharset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public File workingDir() {
    throw new UnsupportedOperationException();
  }

}
