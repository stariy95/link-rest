package io.agrest.cayenne.unit;

import io.bootique.jdbc.junit5.derby.DerbyTester;
import io.bootique.junit5.BQTest;
import io.bootique.junit5.BQTestScope;
import io.bootique.junit5.BQTestTool;

/**
 * An abstract superclass of integration tests that starts Bootique test runtime with JAX-RS service and Derby DB.
 */
@BQTest
public abstract class JerseyAndDerbyCase {

    @BQTestTool(BQTestScope.GLOBAL)
    static final DerbyTester db = DerbyTester.db();

    protected static CayenneAgTester.Builder tester(Class<?>... resources) {
        return CayenneAgTester
                .forDb(db)
                .cayenneProject("cayenne-agrest-tests.xml")
                .resources(resources);
    }
}
