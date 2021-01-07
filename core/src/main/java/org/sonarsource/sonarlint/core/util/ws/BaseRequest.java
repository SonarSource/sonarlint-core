/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.util.ws;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarqube.ws.MediaTypes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

abstract class BaseRequest<SELF extends BaseRequest> implements WsRequest {

  private final String path;

  private String mediaType = MediaTypes.JSON;

  private final DefaultParameters parameters = new DefaultParameters();

  BaseRequest(String path) {
    this.path = path;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public String getMediaType() {
    return mediaType;
  }

  /**
   * Expected media type of response. Default is {@link MediaTypes#JSON}.
   */
  public SELF setMediaType(String s) {
    requireNonNull(s, "media type of response cannot be null");
    this.mediaType = s;
    return (SELF) this;
  }

  public SELF setParam(String key, @Nullable String value) {
    return setSingleValueParam(key, value);
  }

  public SELF setParam(String key, @Nullable Integer value) {
    return setSingleValueParam(key, value);
  }

  public SELF setParam(String key, @Nullable Long value) {
    return setSingleValueParam(key, value);
  }

  public SELF setParam(String key, @Nullable Float value) {
    return setSingleValueParam(key, value);
  }

  public SELF setParam(String key, @Nullable Boolean value) {
    return setSingleValueParam(key, value);
  }

  private SELF setSingleValueParam(String key, @Nullable Object value) {
    checkArgument(isNotEmpty(key), "a WS parameter key cannot be null");
    if (value == null) {
      return (SELF) this;
    }
    parameters.setValue(key, value.toString());

    return (SELF) this;
  }

  public SELF setParam(String key, @Nullable Collection<? extends Object> values) {
    checkArgument(isNotEmpty(key), "a WS parameter key cannot be null");
    if (values == null || values.isEmpty()) {
      return (SELF) this;
    }

    parameters.setValues(key, values.stream()
      .filter(Objects::nonNull)
      .map(Object::toString)
      .filter(value -> !value.isEmpty())
      .collect(Collectors.toList()));

    return (SELF) this;
  }

  @Override
  public Parameters getParameters() {
    return parameters;
  }

  private static class DefaultParameters implements Parameters {
    // preserve insertion order
    private final ListMultimap<String, String> keyValues = LinkedListMultimap.create();

    @Override
    @CheckForNull
    public String getValue(String key) {
      return keyValues.containsKey(key) ? keyValues.get(key).get(0) : null;
    }

    @Override
    public List<String> getValues(String key) {
      return keyValues.get(key);
    }

    @Override
    public Set<String> getKeys() {
      return keyValues.keySet();
    }

    private DefaultParameters setValue(String key, String value) {
      checkArgument(!isNullOrEmpty(key));
      checkArgument(value != null);

      keyValues.putAll(key, singletonList(value));
      return this;
    }

    private DefaultParameters setValues(String key, Collection<String> values) {
      checkArgument(isNotEmpty(key));
      checkArgument(values != null && !values.isEmpty());

      this.keyValues.putAll(key, values.stream().map(Object::toString).filter(Objects::nonNull).collect(Collectors.toList()));

      return this;
    }
  }
}
