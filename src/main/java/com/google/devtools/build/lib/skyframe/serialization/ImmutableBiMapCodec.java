// Copyright 2019 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.skyframe.serialization;

import static com.google.devtools.build.lib.skyframe.serialization.MapHelpers.deserializeMapEntries;
import static com.google.devtools.build.lib.skyframe.serialization.MapHelpers.serializeMapEntries;

import com.google.common.collect.ImmutableBiMap;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Encodes an {@link ImmutableBiMap}. The iteration order of the deserialized map is the same as the
 * original map's.
 *
 * <p>We handle {@link ImmutableBiMap} by treating it as an {@link
 * com/google/devtools/build/lib/skyframe/serialization/ImmutableBiMapCodec.java used only in
 * javadoc: com.google.common.collect.ImmutableMap} and calling the proper conversion method ({@link
 * ImmutableBiMap#copyOf}) when deserializing. This is valid because every {@link ImmutableBiMap} is
 * also an {@link com.google.common.collect.ImmutableMap}.
 *
 * <p>Any {@link SerializationException} or {@link IOException} that arises while serializing or
 * deserializing a map entry's value (not its key) will be wrapped in a new {@link
 * SerializationException} using {@link SerializationException#propagate}. (Note that this preserves
 * the type of {@link SerializationException.NoCodecException} exceptions.) The message will include
 * the {@code toString()} of the entry's key. For errors that occur while serializing, it will also
 * include the class name of the entry's value. Errors that occur while serializing an entry key are
 * not affected.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ImmutableBiMapCodec extends DeferredObjectCodec<ImmutableBiMap> {

  @Override
  public Class<ImmutableBiMap> getEncodedClass() {
    return ImmutableBiMap.class;
  }

  @Override
  public void serialize(
      SerializationContext context, ImmutableBiMap map, CodedOutputStream codedOut)
      throws SerializationException, IOException {
    codedOut.writeInt32NoTag(map.size());
    serializeMapEntries(context, map, codedOut);
  }

  @Override
  public Supplier<ImmutableBiMap> deserializeDeferred(
      AsyncDeserializationContext context, CodedInputStream codedIn)
      throws SerializationException, IOException {
    int size = codedIn.readInt32();
    if (size < 0) {
      throw new SerializationException("Expected non-negative size: " + size);
    }

    if (size == 0) {
      return ImmutableBiMap::of;
    }

    EntryBuffer buffer = new EntryBuffer(size);
    deserializeMapEntries(
        context, codedIn, /* requiresFullValueDeserialization= */ true, buffer.keys, buffer.values);
    return buffer;
  }

  private static class EntryBuffer implements Supplier<ImmutableBiMap> {
    final Object[] keys;
    final Object[] values;

    private EntryBuffer(int size) {
      this.keys = new Object[size];
      this.values = new Object[size];
    }

    int size() {
      return keys.length;
    }

    @Override
    public ImmutableBiMap get() {
      ImmutableBiMap.Builder builder = ImmutableBiMap.builderWithExpectedSize(size());
      for (int i = 0; i < size(); i++) {
        builder.put(keys[i], values[i]);
      }
      return builder.buildOrThrow();
    }
  }
}
