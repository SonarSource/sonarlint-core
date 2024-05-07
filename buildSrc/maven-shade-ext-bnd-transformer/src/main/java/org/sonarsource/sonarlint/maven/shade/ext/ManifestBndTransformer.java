/*
 * Maven Shade Plugin: Transformer for Bndtools
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
package org.sonarsource.sonarlint.maven.shade.ext;

import java.io.File;
import java.io.FileInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ManifestResourceTransformer;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarOutputStream;

/**
 *  A custom Resource Transformer for the Maven Shade plug-in used in combination with the Bndtools for building OSGi bundles that are
 *  consumed by the Eclipse IDE. The Maven plug-in and Bndtools are not optimized to work together but we use them in order to not require
 *  different modules (one for shading, one for OSGi bundle, one for OSGi compliant source bundle) just to complete one operation.
 *
 *  @author tobias.hahnen
 */
public class ManifestBndTransformer implements ReproducibleResourceTransformer {
  private static final String MANIFEST_ATTRIBUTE_BUNDLE_NAME = "Bundle-Name";
  private static final String MANIFEST_ATTRIBUTE_BUNDLE_SYMBOLICNAME = "Bundle-SymbolicName";

  // Configuration of the transformer in the pom.xml
  private String normalJarManifestPath;
  private String sourcesJarManifestPath;

  // Private fields used by the transformer
  private boolean isSourcesJarManifest = false;
  private Manifest normalJarManifest;
  private Manifest sourcesJarManifest;
  private Manifest manifest;
  private long time = Long.MIN_VALUE;


  /** This is used inside the pom.xml for configuring the transformer! */
  public void setNormalJarManifestPath(String normalJarManifestPath) {
    this.normalJarManifestPath = normalJarManifestPath;
  }


  /** This is used inside the pom.xml for configuring the transformer! */
  public void setSourcesJarManifestPath(String sourcesJarManifestPath) {
    this.sourcesJarManifestPath = sourcesJarManifestPath;
  }


  @Override
  public void processResource(String resource, InputStream is, List<Relocator> relocators, long time) throws IOException {
    // i) Load manifest object from the input stream and try to get the information if it
    manifest = new Manifest(is);
    var attributes = manifest.getMainAttributes();

    var bundleName = attributes.getValue(MANIFEST_ATTRIBUTE_BUNDLE_NAME);
    var bundleSymbolicName = attributes.getValue(MANIFEST_ATTRIBUTE_BUNDLE_SYMBOLICNAME);
    info("Processing " + bundleName + " / " + bundleSymbolicName);

    isSourcesJarManifest = isSourcesJarManifest ||
      (bundleName != null && (bundleName.endsWith(" Source") || bundleName.endsWith(" Sources")));
    isSourcesJarManifest = isSourcesJarManifest
      || (bundleSymbolicName != null && (bundleSymbolicName.endsWith(".source") || bundleSymbolicName.endsWith(".sources")));

    // ii) Set the time that is later used when saving the JAR archive entry!
    if (time > this.time) {
      this.time = time;
    }
  }


  @Override
  public void modifyOutputStream(JarOutputStream jos) throws IOException {
    // i) the first time this is called we load the normal / sources JAR manifest file
    tryLoadNormalJarManifest();
    tryLoadSourcesJarManifest();

    // ii) Setup the manifest object that should be written in the end
    if (isSourcesJarManifest && sourcesJarManifest != null) {
      manifest = sourcesJarManifest;
      info("Exchanging META-INF/MANIFEST.MF (sources) with: " + sourcesJarManifestPath);
    } else if (!isSourcesJarManifest && normalJarManifest != null) {
      manifest = normalJarManifest;
      info("Exchanging META-INF/MANIFEST.MF (normal) with: " + normalJarManifestPath);
    } else {
      manifest = manifest != null ? manifest : new Manifest();
      info("Not exchanging META-INF/MANIFEST.MF");
    }

    // iii) Propose output stream content for the META-INF/MANIFEST.MF
    var jarEntry = new JarEntry(JarFile.MANIFEST_NAME);
    jarEntry.setTime(time);
    jos.putNextEntry(jarEntry);
    manifest.write(jos);
  }


  /** Try to load the Manifest with attributes (normal) if not already loaded */
  private void tryLoadNormalJarManifest() throws IOException {
    if (normalJarManifestPath != null && normalJarManifest == null) {
      var manifestFile = new File(normalJarManifestPath);
      if (manifestFile.exists()) {
        normalJarManifest = new Manifest(new FileInputStream(manifestFile));
      } else {
        error("Manifest with attributes (normal) does not exist at: " + normalJarManifestPath);
      }
    }
  }


  /** Try to load the Manifest with attributes (sources) if not already loaded */
  private void tryLoadSourcesJarManifest() throws IOException {
    if (sourcesJarManifestPath != null && sourcesJarManifest == null) {
      var manifestFile = new File(sourcesJarManifestPath);
      if (manifestFile.exists()) {
        sourcesJarManifest = new Manifest(new FileInputStream(manifestFile));
      } else {
        error("Manifest with attributes (sources) does not exist at: " + sourcesJarManifestPath);
      }
    }
  }


  /** Log info to console */
  private static void info(String message) {
    System.err.println("[maven-shade-ext-bnd-transformer : ManifestBndTransformer] " + message);
  }


  /** Log error to console */
  private static void error(String message) {
    System.err.println("[maven-shade-ext-bnd-transformer : ManifestBndTransformer] " + message);
  }


  /**
   *  Implementation is copied from the official implementation of
   *  {@link ManifestResourceTransformer#processResource(String, InputStream, List)}
   */
  @Override
  public void processResource(String resource, InputStream is, List<Relocator> relocators) throws IOException {
    processResource(resource, is, relocators, 0);
  }


  /** Implementation is copied from the official implementation of {@link ManifestResourceTransformer#canTransformResource(String)} */
  @Override
  public boolean canTransformResource(String resource) {
    return JarFile.MANIFEST_NAME.equalsIgnoreCase(resource);
  }


  /**
   * Implementation is copied from the official implementation of {@link ManifestResourceTransformer#hasTransformedResource()}
   */
  @Override
  public boolean hasTransformedResource() {
    return true;
  }
}
