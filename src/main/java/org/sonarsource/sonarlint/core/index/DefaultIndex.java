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
package org.sonarsource.sonarlint.core.index;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.SonarIndex;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.design.Dependency;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.MeasuresFilter;
import org.sonar.api.measures.MeasuresFilters;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.api.resources.Scopes;

public class DefaultIndex extends SonarIndex {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultIndex.class);
  public static final int COMPONENT_KEY_SIZE = 400;

  private final BatchComponentCache componentCache;
  private final Project project;
  // caches
  private Map<Resource, Bucket> buckets = Maps.newLinkedHashMap();

  public DefaultIndex(BatchComponentCache componentCache, Project project) {
    this.componentCache = componentCache;
    this.project = project;
  }

  public void start() {
    Bucket bucket = new Bucket(project);
    addBucket(project, bucket);
    BatchComponent component = componentCache.add(project, null);
    component.setInputComponent(new DefaultInputModule(project.getEffectiveKey()));
  }

  private void addBucket(Resource resource, Bucket bucket) {
    buckets.put(resource, bucket);
  }

  @Override
  public Project getProject() {
    return project;
  }

  @CheckForNull
  @Override
  public Measure getMeasure(Resource resource, org.sonar.api.batch.measure.Metric<?> metric) {
    return getMeasures(resource, MeasuresFilters.metric(metric));
  }

  @CheckForNull
  @Override
  public <M> M getMeasures(Resource resource, MeasuresFilter<M> filter) {
    return null;
  }

  @Override
  public Measure addMeasure(Resource resource, Measure measure) {
    return null;
  }

  @Override
  public Dependency addDependency(Dependency dependency) {
    return dependency;
  }

  @Override
  public Set<Resource> getResources() {
    return buckets.keySet();
  }

  @Override
  public String getSource(Resource reference) {
    return null;
  }

  /**
   * Does nothing if the resource is already registered.
   */
  @Override
  public Resource addResource(Resource resource) {
    Bucket bucket = doIndex(resource);
    return bucket != null ? bucket.getResource() : null;
  }

  @Override
  @CheckForNull
  public <R extends Resource> R getResource(@Nullable R reference) {
    Bucket bucket = getBucket(reference);
    if (bucket != null) {
      return (R) bucket.getResource();
    }
    return null;
  }

  @Override
  public List<Resource> getChildren(Resource resource) {
    List<Resource> children = Lists.newLinkedList();
    Bucket bucket = getBucket(resource);
    if (bucket != null) {
      for (Bucket childBucket : bucket.getChildren()) {
        children.add(childBucket.getResource());
      }
    }
    return children;
  }

  @Override
  public Resource getParent(Resource resource) {
    Bucket bucket = getBucket(resource);
    if (bucket != null && bucket.getParent() != null) {
      return bucket.getParent().getResource();
    }
    return null;
  }

  @Override
  public boolean index(Resource resource) {
    Bucket bucket = doIndex(resource);
    return bucket != null;
  }

  private Bucket doIndex(Resource resource) {
    if (resource.getParent() != null) {
      doIndex(resource.getParent());
    }
    return doIndex(resource, resource.getParent());
  }

  @Override
  public boolean index(Resource resource, Resource parentReference) {
    Bucket bucket = doIndex(resource, parentReference);
    return bucket != null;
  }

  private Bucket doIndex(Resource resource, @Nullable Resource parentReference) {
    Bucket bucket = getBucket(resource);
    if (bucket != null) {
      return bucket;
    }

    if (StringUtils.isBlank(resource.getKey())) {
      LOG.warn("Unable to index a resource without key " + resource);
      return null;
    }

    Resource parent = (Resource) ObjectUtils.defaultIfNull(parentReference, project);

    Bucket parentBucket = getBucket(parent);
    if (parentBucket == null && parent != null) {
      LOG.warn("Resource ignored, parent is not indexed: " + resource);
      return null;
    }

    if (ResourceUtils.isProject(resource) || /* For technical projects */ResourceUtils.isRootProject(resource)) {
      resource.setEffectiveKey(resource.getKey());
    } else {
      resource.setEffectiveKey(createEffectiveKey(project, resource));
    }
    bucket = new Bucket(resource).setParent(parentBucket);
    addBucket(resource, bucket);

    Resource parentResource = parentBucket != null ? parentBucket.getResource() : null;
    BatchComponent component = componentCache.add(resource, parentResource);
    if (ResourceUtils.isProject(resource)) {
      component.setInputComponent(new DefaultInputModule(resource.getEffectiveKey()));
    }

    return bucket;
  }

  /**
   * @return the full key of a component, based on its parent projects' key and own key
   */
  public static String createEffectiveKey(Project project, Resource resource) {
    String key = resource.getKey();
    if (!StringUtils.equals(Scopes.PROJECT, resource.getScope())) {
      // not a project nor a library
      key = new StringBuilder(COMPONENT_KEY_SIZE)
        .append(project.getKey())
        .append(':')
        .append(resource.getKey())
        .toString();
    }
    return key;
  }

  @Override
  public boolean isExcluded(@Nullable Resource reference) {
    return false;
  }

  @Override
  public boolean isIndexed(@Nullable Resource reference, boolean acceptExcluded) {
    return getBucket(reference) != null;
  }

  private Bucket getBucket(@Nullable Resource reference) {
    if (reference == null) {
      return null;
    }
    if (StringUtils.isNotBlank(reference.getKey())) {
      return buckets.get(reference);
    }
    return null;
  }

}
