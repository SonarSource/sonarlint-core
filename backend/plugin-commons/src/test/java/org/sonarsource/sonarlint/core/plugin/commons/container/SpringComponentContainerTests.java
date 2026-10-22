/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) SonarSource Sàrl
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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.Property;
import org.sonar.api.Startable;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringComponentContainerTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  @Test
  void should_stop_after_failing() {
    ApiStartable startStop = new ApiStartable();
    SpringComponentContainer container = new SpringComponentContainer() {
      @Override
      public void doBeforeStart() {
        add(startStop);
      }

      @Override
      public void doAfterStart() {
        getComponentByType(ApiStartable.class);
        throw new IllegalStateException("doBeforeStart");
      }
    };

    assertThrows(IllegalStateException.class, () -> container.execute(null));
    assertThat(startStop.start).isOne();
    assertThat(startStop.stop).isOne();
  }

  @Test
  void add_registers_instance_with_toString() {
    SpringComponentContainer container = new SimpleContainer(new ToString("a"), new ToString("b"));
    container.startComponents();
    assertThat(container.context.getBeanDefinitionNames())
      .contains(
        this.getClass().getClassLoader() + "-org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests.ToString-a",
        this.getClass().getClassLoader() + "-org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests.ToString-b");
    assertThat(container.getComponentsByType(ToString.class)).hasSize(2);
  }

  @Test
  void add_registers_class_with_classloader_and_fqcn() {
    SpringComponentContainer container = new SimpleContainer(A.class, B.class);
    container.startComponents();
    assertThat(container.context.getBeanDefinitionNames())
      .contains(
        this.getClass().getClassLoader() + "-org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests.A",
        this.getClass().getClassLoader() + "-org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests.B");
    assertThat(container.getComponentByType(A.class)).isNotNull();
    assertThat(container.getComponentByType(B.class)).isNotNull();
  }

  @Test
  void get_optional_component_by_type_should_return_correctly() {
    SpringComponentContainer container = new SpringComponentContainer();
    container.add(A.class);
    container.startComponents();
    assertThat(container.getOptionalComponentByType(A.class)).containsInstanceOf(A.class);
    assertThat(container.getOptionalComponentByType(B.class)).isEmpty();
  }

  @Test
  void safe_lookup_should_skip_creation_failures_without_eagerly_creating_later_components() {
    var container = new SimpleContainer(A.class, ConstructorFailure.class, ConstructorError.class, DependencyFailure.class, B.class);
    container.add(FailingDependency.class);
    container.startComponents();
    var failures = new LinkedHashMap<String, Throwable>();

    var components = container.getComponentsByTypeSafely(ObjectComponent.class, (name, failure) -> {
      assertThat(container.context.getBeanFactory().containsSingleton(componentName(B.class))).isFalse();
      failures.put(name, failure);
    });

    assertThat(components).extracting(Object::getClass).containsExactly(A.class, B.class);
    assertThat(failures).containsOnlyKeys(componentName(ConstructorFailure.class), componentName(ConstructorError.class), componentName(DependencyFailure.class));
    assertThat(failures.values()).allMatch(BeanCreationException.class::isInstance);
    assertThat(failures.get(componentName(ConstructorFailure.class))).hasRootCauseMessage("constructor failure");
    assertThat(failures.get(componentName(ConstructorError.class))).hasRootCauseInstanceOf(LinkageError.class).hasRootCauseMessage("constructor error");
    assertThat(failures.get(componentName(DependencyFailure.class))).hasRootCauseMessage("dependency failure");
    container.stopComponents();
  }

  @Test
  void safe_lookup_should_return_empty_when_no_components_succeed() {
    var container = new SpringComponentContainer();
    container.startComponents();
    var failures = new LinkedHashMap<String, Throwable>();
    assertThat(container.getComponentsByTypeSafely(ObjectComponent.class, failures::put)).isEmpty();
    assertThat(failures).isEmpty();
    container.stopComponents();

    var failingContainer = new SimpleContainer(ConstructorFailure.class);
    failingContainer.startComponents();
    assertThat(failingContainer.getComponentsByTypeSafely(ObjectComponent.class, failures::put)).isEmpty();
    assertThat(failures).containsOnlyKeys(componentName(ConstructorFailure.class));
    failingContainer.stopComponents();
  }

  @Test
  void safe_lookup_should_include_ancestors_and_preserve_child_shadowing() {
    var grandparent = new SimpleContainer(A.class);
    grandparent.context.registerBean("grandparent", ObjectComponent.class, B::new);
    var parent = grandparent.createChild();
    parent.add(B.class);
    var child = parent.createChild();
    child.add(A.class);
    grandparent.startComponents();
    parent.startComponents();
    child.startComponents();

    var components = child.getComponentsByTypeSafely(ObjectComponent.class, (name, failure) -> {
      throw new AssertionError(failure);
    });

    assertThat(components).containsExactly(child.getComponentByType(A.class), parent.getComponentByType(B.class), grandparent.context.getBean("grandparent", ObjectComponent.class));
    assertThat(components.getFirst()).isNotSameAs(grandparent.getComponentByType(A.class));
    grandparent.stopComponents();
  }

  @Test
  void safe_lookup_should_not_fall_back_to_an_ancestor_when_a_shadowing_child_fails() {
    var parent = new SpringComponentContainer();
    parent.context.registerBean("shared", ObjectComponent.class, A::new);
    var child = parent.createChild();
    child.context.registerBean("shared", ConstructorFailure.class);
    parent.startComponents();
    child.startComponents();
    var failures = new LinkedHashMap<String, Throwable>();

    assertThat(child.getComponentsByTypeSafely(ObjectComponent.class, failures::put)).isEmpty();
    assertThat(failures).containsOnlyKeys("shared");
    assertThat(parent.context.getBeanFactory().containsSingleton("shared")).isFalse();
    parent.stopComponents();
  }

  @Test
  void safe_lookup_should_use_the_dependency_comparator_with_stable_ties() {
    var tie1 = new OrderedComponent(3);
    var tie2 = new OrderedComponent(3);
    var first = new OrderedComponent(0);
    var annotated = new AnnotatedComponent();
    var priority = new PriorityComponent();
    var container = new SimpleContainer(tie1, annotated, priority, tie2, first, ConstructorFailure.class);
    container.startComponents();

    assertThat(container.getComponentsByTypeSafely(ObjectComponent.class, (name, failure) -> {}))
      .containsExactly(first, priority, annotated, tie1, tie2);
    container.context.getDefaultListableBeanFactory().setDependencyComparator(null);
    assertThat(container.getComponentsByTypeSafely(ObjectComponent.class, (name, failure) -> {}))
      .containsExactly(tie1, annotated, priority, tie2, first);
    container.stopComponents();
  }

  @Test
  void safe_lookup_should_propagate_callback_and_sorting_failures() {
    var container = new SimpleContainer(ConstructorFailure.class, A.class, B.class);
    container.startComponents();
    var failure = new IllegalStateException("callback failure");
    assertThatThrownBy(() -> container.getComponentsByTypeSafely(ObjectComponent.class, (name, throwable) -> {
      throw failure;
    })).isSameAs(failure);

    container.context.getDefaultListableBeanFactory().setDependencyComparator((first, second) -> {
      throw failure;
    });
    assertThatThrownBy(() -> container.getComponentsByTypeSafely(ObjectComponent.class, (name, throwable) -> {})).isSameAs(failure);
    container.stopComponents();
  }

  @Test
  void safe_lookup_should_propagate_discovery_failures() {
    var container = new SimpleContainer(A.class);

    assertThatThrownBy(() -> container.getComponentsByTypeSafely(ObjectComponent.class, (name, failure) -> {
      throw new AssertionError("Discovery failure should propagate", failure);
    })).isInstanceOf(IllegalStateException.class).hasMessageContaining("has not been refreshed yet");
  }

  @Test
  void existing_getters_should_still_fail_on_creation_errors() {
    var container = new SimpleContainer(ConstructorFailure.class);
    container.startComponents();
    assertThatThrownBy(() -> container.getComponentByType(ConstructorFailure.class)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("constructor failure");
    assertThatThrownBy(() -> container.getComponentsByType(ObjectComponent.class)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("constructor failure");
    assertThatThrownBy(() -> container.getOptionalComponentByType(ConstructorFailure.class)).isInstanceOf(BeanCreationException.class).hasRootCauseMessage("constructor failure");
    container.stopComponents();
  }

  private static String componentName(Class<?> type) {
    return new ComponentKeys().ofClass(type);
  }

  @Test
  void createChild_method_should_spawn_a_child_container() {
    SpringComponentContainer parent = new SpringComponentContainer();
    SpringComponentContainer child = parent.createChild();
    assertThat(child).isNotEqualTo(parent);
    assertThat(child.parent).isEqualTo(parent);
    assertThat(parent.children).contains(child);
  }

  @Test
  void get_component_by_type_should_throw_exception_when_type_does_not_exist() {
    SpringComponentContainer container = new SpringComponentContainer();
    container.add(A.class);
    container.startComponents();
    assertThatThrownBy(() -> container.getComponentByType(B.class))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Unable to load component class org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests$B");
  }

  @Test
  void should_throw_start_exception_if_stop_also_throws_exception() {
    ErrorStopClass errorStopClass = new ErrorStopClass();
    SpringComponentContainer container = new SpringComponentContainer() {
      @Override
      public void doBeforeStart() {
        add(errorStopClass);
      }

      @Override
      public void doAfterStart() {
        getComponentByType(ErrorStopClass.class);
        throw new IllegalStateException("doBeforeStart");
      }
    };
    assertThrows(IllegalStateException.class, () -> container.execute(null));
    assertThat(errorStopClass.stopped).isTrue();
  }

  @Test
  void addExtension_supports_extensions_without_annotations() {
    SpringComponentContainer container = new SimpleContainer(A.class, B.class);
    container.addExtension("", ExtensionWithMultipleConstructorsAndNoAnnotations.class);
    container.startComponents();
    assertThat(container.getComponentByType(ExtensionWithMultipleConstructorsAndNoAnnotations.class).gotBothArgs).isTrue();
  }

  @Test
  void addExtension_supports_extension_instances_without_annotations() {
    SpringComponentContainer container = new SpringComponentContainer();
    container.addExtension("", new ExtensionWithMultipleConstructorsAndNoAnnotations(new A()));
    container.startComponents();
    assertThat(container.getComponentByType(ExtensionWithMultipleConstructorsAndNoAnnotations.class)).isNotNull();
  }

  @Test
  void addExtension_resolves_iterables() {
    List<Class<?>> classes = Arrays.asList(A.class, B.class);
    SpringComponentContainer container = new SpringComponentContainer();
    container.addExtension("", classes);
    container.startComponents();
    assertThat(container.getComponentByType(A.class)).isNotNull();
    assertThat(container.getComponentByType(B.class)).isNotNull();
  }

  @Test
  void declareExtension_adds_property() {
    SpringComponentContainer container = new SpringComponentContainer();
    container.addExtension("myPlugin", A.class);

    container.startComponents();
    PropertyDefinitions propertyDefinitions = container.getComponentByType(PropertyDefinitions.class);
    PropertyDefinition propertyDefinition = propertyDefinitions.get("k");
    assertThat(propertyDefinition.key()).isEqualTo("k");
    assertThat(propertyDefinitions.getCategory("k")).isEmpty();
  }

  @Test
  void stop_should_stop_children() {
    SpringComponentContainer parent = new SpringComponentContainer();
    ApiStartable s1 = new ApiStartable();
    parent.add(s1);
    parent.startComponents();
    SpringComponentContainer child = new SpringComponentContainer(parent);
    assertThat(child.getParent()).isEqualTo(parent);
    assertThat(parent.children).containsOnly(child);
    ApiStartable s2 = new ApiStartable();
    child.add(s2);
    child.startComponents();

    parent.stopComponents();
    assertThat(s1.stop).isOne();
    assertThat(s2.stop).isOne();
  }

  @Test
  void stop_should_remove_container_from_parent() {
    SpringComponentContainer parent = new SpringComponentContainer();
    SpringComponentContainer child = new SpringComponentContainer(parent);
    assertThat(parent.children).containsOnly(child);
    child.stopComponents();
    assertThat(parent.children).isEmpty();
  }

  @Test
  void bean_create_fails_if_class_has_default_constructor_and_other_constructors() {
    SpringComponentContainer container = new SpringComponentContainer();
    container.add(ClassWithMultipleConstructorsIncNoArg.class);
    container.startComponents();
    assertThatThrownBy(() -> container.getComponentByType(ClassWithMultipleConstructorsIncNoArg.class))
      .hasRootCauseMessage(
        "Constructor annotations missing in: class org.sonarsource.sonarlint.core.plugin.commons.container.SpringComponentContainerTests$ClassWithMultipleConstructorsIncNoArg");
  }

  @Test
  void support_start_stop_callbacks() {
    JsrLifecycleCallbacks jsr = new JsrLifecycleCallbacks();
    ApiStartable api = new ApiStartable();
    AutoClose closeable = new AutoClose();

    SpringComponentContainer container = new SimpleContainer(jsr, api, closeable) {
      @Override
      public void doAfterStart() {
        // force lazy instantiation
        getComponentByType(JsrLifecycleCallbacks.class);
        getComponentByType(ApiStartable.class);
        getComponentByType(AutoClose.class);
      }
    };
    container.execute(null);

    assertThat(closeable.closed).isOne();
    assertThat(jsr.postConstruct).isOne();
    assertThat(jsr.preDestroy).isOne();
    assertThat(api.start).isOne();
    assertThat(api.stop).isOne();
  }

  private static class JsrLifecycleCallbacks {
    private int postConstruct = 0;
    private int preDestroy = 0;

    @PostConstruct
    public void postConstruct() {
      postConstruct++;
    }

    @PreDestroy
    public void preDestroy() {
      preDestroy++;
    }
  }

  private static class AutoClose implements AutoCloseable {
    private int closed = 0;

    @Override
    public void close() {
      closed++;
    }
  }

  private static class ApiStartable implements Startable {
    private int start = 0;
    private int stop = 0;

    @Override
    public void start() {
      start++;
    }

    @Override
    public void stop() {
      stop++;
    }
  }

  private static class ToString {
    private final String toString;

    public ToString(String toString) {
      this.toString = toString;
    }

    @Override
    public String toString() {
      return toString;
    }
  }

  @Property(key = "k", name = "name")
  private static class A implements ObjectComponent {
  }

  private static class B implements ObjectComponent {
  }

  private interface ObjectComponent {
  }

  private static class ConstructorFailure implements ObjectComponent {
    public ConstructorFailure() {
      throw new IllegalStateException("constructor failure");
    }
  }

  private static class ConstructorError implements ObjectComponent {
    public ConstructorError() {
      throw new LinkageError("constructor error");
    }
  }

  private static class FailingDependency {
    public FailingDependency() {
      throw new IllegalStateException("dependency failure");
    }
  }

  private static class DependencyFailure implements ObjectComponent {
    public DependencyFailure(FailingDependency dependency) {
      // The dependency fails before this constructor is invoked.
    }
  }

  private static class OrderedComponent implements ObjectComponent, Ordered {
    private final int order;

    public OrderedComponent(int order) {
      this.order = order;
    }

    @Override
    public int getOrder() {
      return order;
    }
  }

  @Order(2)
  private static class AnnotatedComponent implements ObjectComponent {
  }

  @Priority(1)
  private static class PriorityComponent implements ObjectComponent {
  }

  private static class ClassWithMultipleConstructorsIncNoArg {
    public ClassWithMultipleConstructorsIncNoArg() {
    }

    public ClassWithMultipleConstructorsIncNoArg(A a) {
      // intentionally empty, used to test multiple constructors
    }
  }

  private static class ExtensionWithMultipleConstructorsAndNoAnnotations {
    private boolean gotBothArgs = false;

    public ExtensionWithMultipleConstructorsAndNoAnnotations(A a) {
      // intentionally empty, used to test multiple constructors
    }

    public ExtensionWithMultipleConstructorsAndNoAnnotations(A a, B b) {
      gotBothArgs = true;
    }
  }

  private static class ErrorStopClass implements Startable {
    private boolean stopped = false;

    @Override
    public void start() {
      // intentionally empty, only stop() is overridden to throw an error
    }

    @Override
    public void stop() {
      stopped = true;
      throw new IllegalStateException("stop");
    }
  }

  private static class SimpleContainer extends SpringComponentContainer {
    public SimpleContainer(Object... objects) {
      add(objects);
    }
  }
}
