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

package io.jmix.graphql.service

import com.graphql.spring.boot.test.GraphQLTestTemplate
import io.jmix.graphql.AbstractGraphQLTest
import org.springframework.beans.factory.annotation.Autowired

class UserInfoServiceTest extends AbstractGraphQLTest {

    @Autowired
    GraphQLTestTemplate graphQLTestTemplate

    def "userInfo query works for admin user"() {
        when:
        def response = query("service/userInfo.graphql")

        then:
        getBody(response) == '{"data":{' +
                '"userInfo":{' +
                '"username":"admin",' +
                '"locale":"en"' +
                '}}}'
    }

    def "userInfo query works for mechanic user"() {
        when:
        def response = query("service/userInfo.graphql", null, mechanicToken)

        then:
        getBody(response) == '{"data":{' +
                '"userInfo":{' +
                '"username":"mechanic",' +
                '"locale":"en"' +
                '}}}'
    }
}
