/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.container;

import com.google.common.collect.Iterables;
import java.lang.annotation.Annotation;
import java.util.List;
import javax.annotation.Nullable;
import org.picocontainer.Characteristics;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.ComponentFactory;
import org.picocontainer.ComponentMonitor;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.LifecycleStrategy;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.PicoContainer;
import org.picocontainer.behaviors.OptInCaching;
import org.picocontainer.lifecycle.ReflectionLifecycleStrategy;
import org.picocontainer.monitors.NullComponentMonitor;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.sonarlint.core.plugin.PluginInfo;

public class ComponentContainer implements ContainerPopulator.Container {

  private static final class ExtendedDefaultPicoContainer extends DefaultPicoContainer {
    private ExtendedDefaultPicoContainer(ComponentFactory componentFactory, LifecycleStrategy lifecycleStrategy, PicoContainer parent) {
      super(componentFactory, lifecycleStrategy, parent);
    }

    private ExtendedDefaultPicoContainer(final ComponentFactory componentFactory, final LifecycleStrategy lifecycleStrategy, final PicoContainer parent,
      final ComponentMonitor componentMonitor) {
      super(componentFactory, lifecycleStrategy, parent, componentMonitor);
    }

    @Override
    public Object getComponent(final Object componentKeyOrType, final Class<? extends Annotation> annotation) {
      try {
        return super.getComponent(componentKeyOrType, annotation);
      } catch (Throwable t) {
        throw new IllegalStateException("Unable to load component " + componentKeyOrType, t);
      }
    }

    @Override
    public MutablePicoContainer makeChildContainer() {
      DefaultPicoContainer pc = new ExtendedDefaultPicoContainer(componentFactory, lifecycleStrategy, this, componentMonitor);
      addChildContainer(pc);
      return pc;
    }
  }

  ComponentContainer parent;
  MutablePicoContainer pico;
  PropertyDefinitions propertyDefinitions;
  PicoComponentKeys componentKeys;

  /**
   * Create root container
   */
  public ComponentContainer() {
    this(createPicoContainer());
  }

  protected ComponentContainer(MutablePicoContainer picoContainer) {
    this.parent = null;
    this.pico = picoContainer;
    this.componentKeys = new PicoComponentKeys();
    propertyDefinitions = new PropertyDefinitions(System2.INSTANCE);
    addSingleton(propertyDefinitions);
    addSingleton(this);
  }

  /**
   * Create child container
   */
  protected ComponentContainer(ComponentContainer parent) {
    this.parent = parent;
    this.pico = parent.makeChildContainer();
    this.propertyDefinitions = parent.propertyDefinitions;
    this.componentKeys = new PicoComponentKeys();
    addSingleton(this);
  }

  private synchronized MutablePicoContainer makeChildContainer() {
    return pico.makeChildContainer();
  }

  public void execute() {
    boolean threw = true;
    try {
      startComponents();
      threw = false;
    } finally {
      stopComponents(threw);
    }
  }

  /**
   * This method MUST NOT be renamed start() because the container is registered itself in picocontainer. Starting
   * a component twice is not authorized.
   */
  public ComponentContainer startComponents() {
    try {
      doBeforeStart();
      pico.start();
      doAfterStart();
      return this;
    } catch (Exception e) {
      throw PicoUtils.propagate(e);
    }
  }

  /**
   * This method aims to be overridden
   */
  protected void doBeforeStart() {
    // nothing
  }

  /**
   * This method aims to be overridden
   */
  protected void doAfterStart() {
    // nothing
  }

  /**
   * This method MUST NOT be renamed stop() because the container is registered itself in picocontainer. Starting
   * a component twice is not authorized.
   */
  public ComponentContainer stopComponents() {
    return stopComponents(false);
  }

  public ComponentContainer stopComponents(boolean swallowException) {
    try {
      pico.stop();
      pico.dispose();

    } catch (RuntimeException e) {
      if (!swallowException) {
        throw PicoUtils.propagate(e);
      }
    } finally {
      if (parent != null) {
        parent.removeChildContainer(pico);
      }
    }
    return this;
  }

  private synchronized void removeChildContainer(MutablePicoContainer child) {
    this.pico.removeChildContainer(child);
  }

  /**
   * @since 3.5
   */
  @Override
  public ComponentContainer add(Object... objects) {
    for (Object object : objects) {
      if (object instanceof ComponentAdapter) {
        addPicoAdapter((ComponentAdapter) object);
      } else if (object instanceof Iterable) {
        add(Iterables.toArray((Iterable) object, Object.class));
      } else {
        addSingleton(object);
      }
    }
    return this;
  }

  public void addIfMissing(Object object, Class<?> objectType) {
    if (getComponentByType(objectType) == null) {
      add(object);
    }
  }

  @Override
  public ComponentContainer addSingletons(Iterable<?> components) {
    for (Object component : components) {
      addSingleton(component);
    }
    return this;
  }

  public ComponentContainer addSingleton(Object component) {
    Object key = componentKeys.of(component);
    if (component instanceof ComponentAdapter) {
      pico.addAdapter((ComponentAdapter) component);
    } else {
      try {
        pico.as(Characteristics.CACHE).addComponent(key, component);
      } catch (Throwable t) {
        throw new IllegalStateException("Unable to register component " + getName(component), t);
      }
      declareExtension(null, component);
    }
    return this;
  }

  public ComponentContainer addExtension(@Nullable PluginInfo pluginInfo, Object extension) {
    Object key = componentKeys.of(extension);
    try {
      pico.as(Characteristics.CACHE).addComponent(key, extension);
    } catch (Throwable t) {
      throw new IllegalStateException("Unable to register extension " + getName(extension), t);
    }
    declareExtension(pluginInfo, extension);
    return this;
  }

  private static String getName(Object extension) {
    if (extension instanceof Class) {
      return ((Class<?>) extension).getName();
    }
    return getName(extension.getClass());
  }

  public void declareExtension(@Nullable PluginInfo pluginInfo, Object extension) {
    propertyDefinitions.addComponent(extension, pluginInfo != null ? pluginInfo.getName() : "");
  }

  public ComponentContainer addPicoAdapter(ComponentAdapter<?> adapter) {
    pico.addAdapter(adapter);
    return this;
  }

  @Override
  public <T> T getComponentByType(Class<T> type) {
    return pico.getComponent(type);
  }

  public Object getComponentByKey(Object key) {
    return pico.getComponent(key);
  }

  public <T> List<T> getComponentsByType(Class<T> tClass) {
    return pico.getComponents(tClass);
  }

  public ComponentContainer createChild() {
    return new ComponentContainer(this);
  }

  public static MutablePicoContainer createPicoContainer() {
    ReflectionLifecycleStrategy lifecycleStrategy = new ReflectionLifecycleStrategy(new NullComponentMonitor(), "start", "stop", "close") {
      @Override
      public void start(Object component) {
        Profiler profiler = Profiler.createIfTrace(Loggers.get(ComponentContainer.class));
        profiler.start();
        super.start(component);
        profiler.stopTrace(component.getClass().getCanonicalName() + " started");
      }
    };
    return new ExtendedDefaultPicoContainer(new OptInCaching(), lifecycleStrategy, null);
  }

  public ComponentContainer getParent() {
    return parent;
  }

  public MutablePicoContainer getPicoContainer() {
    return pico;
  }

  public int size() {
    return pico.getComponentAdapters().size();
  }
}
