/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.adapter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import javax.annotation.Nullable;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.json.adapters.JsonElementTypeAdapter} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public class JsonElementTypeAdapter extends TypeAdapter<Object> {

  /**
   * This factory should not be registered with a GsonBuilder because it always matches.
   * Use it as argument to a {@link com.google.gson.annotations.JsonAdapter} annotation like this:
   * {@code @JsonAdapter(JsonElementTypeAdapter.Factory.class)}
   */
  public static class Factory implements TypeAdapterFactory {

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      return (TypeAdapter<T>) new JsonElementTypeAdapter(gson);
    }

  }

  private final Gson gson;
  private final TypeAdapter<JsonElement> adapter;

  public JsonElementTypeAdapter(Gson gson) {
    this.gson = gson;
    this.adapter = gson.getAdapter(JsonElement.class);
  }

  @Override
  public JsonElement read(JsonReader in) throws IOException {
    return adapter.read(in);
  }

  @Override
  public void write(JsonWriter out, @Nullable Object value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else if (value instanceof JsonElement) {
      adapter.write(out, (JsonElement) value);
    } else {
      gson.toJson(value, value.getClass(), out);
    }
  }
}
