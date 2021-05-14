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

package io.jmix.graphql.schema.scalar;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;

public abstract class BaseDateCoercing implements Coercing {

    @Override
    public Object parseValue(Object input) {
        if (input instanceof String) {
            String value = (String) input;
            return parseString(value);
        }
        throw new CoercingParseLiteralException(
                "Expected type 'String' but was '" + input.getClass().getSimpleName() + "'.");
    }

    @Override
    public Object parseLiteral(Object input) {
        if (input instanceof StringValue) {
            String value = ((StringValue) input).getValue();
            return parseString(value);
        }
        throw new CoercingParseLiteralException(
                "Expected type 'StringValue' but was '" + input.getClass().getSimpleName() + "'.");

    }

    protected abstract Object parseString(String value);

}
