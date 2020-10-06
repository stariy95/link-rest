package io.agrest.cayenne.it;

import io.agrest.Ag;
import io.agrest.DataResponse;
import io.agrest.SelectStage;
import io.agrest.cayenne.unit.CayenneAgTester;
import io.agrest.cayenne.unit.JerseyAndDerbyCase;
import io.agrest.it.fixture.cayenne.E14;
import io.agrest.it.fixture.cayenne.E15;
import io.agrest.it.fixture.pojo.model.P7;
import io.agrest.runtime.processor.select.SelectContext;
import io.bootique.junit5.BQTestTool;
import org.apache.cayenne.Cayenne;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

public class GET_PersistentWithExtraAnnotatedPropertiesIT extends JerseyAndDerbyCase {

    @BQTestTool
    static final CayenneAgTester tester = tester(Resource.class)

            .entities(E14.class, E15.class)
            .build();

    // TODO: each test is using the same dataset... if we could only do data cleanup once per class, then we can load
    //  the test data in constructor

    @Test
    public void testGET_Root() {

        tester.e15().insertColumns("long_id", "name").values(1L, "xxx").exec();
        tester.e14().insertColumns("e15_id", "long_id", "name").values(1L, 8L, "yyy").exec();

        tester.target("/e14")
                .queryParam("include", "name", "prettyName")
                .get().wasSuccess().bodyEquals(1, "{\"name\":\"yyy\",\"prettyName\":\"yyy_pretty\"}");
    }

    @Test
    public void testIncludeRelationship() {

        tester.e15().insertColumns("long_id", "name").values(1L, "xxx").exec();
        tester.e14().insertColumns("e15_id", "long_id", "name").values(1L, 8L, "yyy").exec();

        tester.target("/e14")
                .queryParam("include", "name", "p7")

                .get().wasSuccess().bodyEquals(1, "{\"name\":\"yyy\",\"p7\":{\"id\":800,\"string\":\"p7_yyy\"}}");
    }

    @Test
    public void testGET_Related() {

        tester.e15().insertColumns("long_id", "name").values(1L, "xxx").exec();
        tester.e14().insertColumns("e15_id", "long_id", "name").values(1L, 8L, "yyy").exec();

        tester.target("/e15")
                .queryParam("include", "e14s.name", "e14s.prettyName")
                .get()
                .wasSuccess().bodyEquals(1, "{\"id\":1,\"e14s\":[{\"name\":\"yyy\",\"prettyName\":\"yyy_pretty\"}],\"name\":\"xxx\"}");
    }

    @Path("")
    public static class Resource {

        @Context
        private Configuration config;

        @GET
        @Path("e15")
        public DataResponse<E15> getE15(@Context UriInfo uriInfo) {
            return Ag.select(E15.class, config).uri(uriInfo).get();
        }

        @GET
        @Path("e14")
        public DataResponse<E14> getE14(@Context UriInfo uriInfo) {
            return Ag.select(E14.class, config)
                    .stage(SelectStage.FETCH_DATA, (SelectContext<E14> c) -> afterE14Fetched(c))
                    .uri(uriInfo).get();
        }

        void afterE14Fetched(SelectContext<E14> context) {
            for (E14 e14 : context.getEntity().getResult()) {
                P7 p7 = new P7();
                p7.setId(Cayenne.intPKForObject(e14) * 100);
                p7.setString("p7_" + e14.getName());
                e14.setP7(p7);
            }
        }
    }
}
