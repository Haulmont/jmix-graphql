/*
 * Copyright 2021 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jmix.graphql.schema.scalar.coercing;

import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class LongCoercing extends BaseScalarCoercing {

    @Override
    public Object serialize(Object input) throws CoercingSerializeException {
        return (input instanceof Long) ? String.valueOf(input) : null;
    }

    @Override
    public Object parseValue(Object input) throws CoercingParseValueException {
        return (input instanceof String) ? Long.parseLong((String) input) : null;
    }

}
