import io.jmix.core.CoreConfiguration
import io.jmix.data.DataConfiguration
import io.jmix.eclipselink.EclipselinkConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification
import test_support.GraphQLTestConfiguration
import test_support.TestContextInitializer

/*
 * Copyright 2019 Haulmont.
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

@ContextConfiguration(
        classes = [CoreConfiguration, DataConfiguration, EclipselinkConfiguration, GraphQLTestConfiguration],
        initializers = [TestContextInitializer]
)
class AbstractGraphQLTest extends Specification {

    @Autowired
    protected JdbcTemplate jdbc

    protected TransactionTemplate transaction

    @Autowired
    protected void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void clearTables(String... tableNames) {
        tableNames.each {
            clearTable(it)
        }
    }

    protected void clearTable(String tableName) {
        //runSqlUpdate("CREATE TABLE IF NOT EXISTS $tableName")
        runSqlUpdate("delete from $tableName")
    }

    protected void runSqlUpdate(String sqlUpdateString) {
        transaction.executeWithoutResult({ status ->
            jdbc.update(sqlUpdateString)
        })
    }
}
