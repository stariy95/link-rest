package io.agrest.cayenne;

import io.agrest.Ag;
import io.agrest.DataResponse;
import io.agrest.SimpleResponse;
import io.agrest.cayenne.cayenne.main.*;
import io.agrest.cayenne.unit.AgCayenneTester;
import io.agrest.cayenne.unit.DbTest;
import io.agrest.constraints.Constraint;
import io.bootique.junit5.BQTestTool;
import org.junit.jupiter.api.Test;

import javax.ws.rs.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class POST_IT extends DbTest {

    @BQTestTool
    static final AgCayenneTester tester = tester(Resource.class)
            .entities(E2.class, E3.class, E4.class, E8.class, E16.class, E17.class, E19.class)
            .build();

    @Test
    public void test() {

        tester.target("/e4").post("{\"cVarchar\":\"zzz\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"cBoolean\":null,\"cDate\":null,\"cDecimal\":null,\"cInt\":null,"
                        + "\"cTime\":null,\"cTimestamp\":null,\"cVarchar\":\"zzz\"}");

        tester.e4().matcher().assertOneMatch();
        tester.e4().matcher().eq("c_varchar", "zzz").assertOneMatch();

        tester.target("/e4").post("{\"cVarchar\":\"TTTT\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"cBoolean\":null,\"cDate\":null,\"cDecimal\":null,\"cInt\":null,"
                        + "\"cTime\":null,\"cTimestamp\":null,\"cVarchar\":\"TTTT\"}");

        tester.e4().matcher().assertMatches(2);
        tester.e4().matcher().eq("c_varchar", "TTTT").assertOneMatch();
    }

    @Test
    public void testCompoundId() {

        tester.target("/e17")
                .queryParam("id1", 1)
                .queryParam("id2", 1)
                .post("{\"name\":\"xxx\"}")
                .wasCreated()
                .bodyEquals(1, "{\"id\":{\"id1\":1,\"id2\":1},\"id1\":1,\"id2\":1,\"name\":\"xxx\"}");
    }

    @Test
    public void testDateTime() {
        tester.target("e16")
                .post("{\"cDate\":\"2015-03-14\", \"cTime\":\"T19:00:00\", \"cTimestamp\":\"2015-03-14T19:00:00.000\"}")
                .wasCreated()
                // TODO: why is time returned back without a "T" prefix?
                .bodyEquals(1, "{\"id\":1,\"cDate\":\"2015-03-14\",\"cTime\":\"19:00:00\",\"cTimestamp\":\"2015-03-14T19:00:00\"}");
    }

    @Test
    public void testSync_NoData() {

        tester.target("/e4/sync")
                .post("{\"cVarchar\":\"zzz\"}")
                .wasCreated()
                .bodyEquals("{\"success\":true}");

        tester.e4().matcher().assertOneMatch();
        tester.e4().matcher().eq("c_varchar", "zzz").assertOneMatch();
    }

    @Test
    public void testWriteConstraints_Id_Allowed() {

        // endpoint constraint allows "name" and "id"

        tester.target("/e8/w/constrainedid/578")
                .post("{\"name\":\"zzz\"}")
                .wasCreated()
                .bodyEquals("{\"success\":true}");

        tester.e8().matcher().assertOneMatch();
        tester.e8().matcher().eq("id", 578).eq("name", "zzz").assertOneMatch();
    }

    @Test
    public void testWriteConstraints_Id_Blocked() {

        // endpoint constraint allows "name", but not "id"

        tester.target("/e8/w/constrainedidblocked/578")
                .post("{\"name\":\"zzz\"}")
                .wasBadRequest()
                .bodyEquals("{\"success\":false,\"message\":\"Setting ID explicitly is not allowed: {id=578}\"}");

        tester.e8().matcher().assertNoMatches();
    }

    @Test
    public void testWriteConstraints1() {

        tester.target("/e3/w/constrained")
                .post("{\"name\":\"zzz\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"name\":\"zzz\",\"phoneNumber\":null}");
    }

    @Test
    public void testWriteConstraints2() {

        tester.target("/e3/w/constrained")
                .post("{\"name\":\"zzz\",\"phoneNumber\":\"12345\"}")
                .wasCreated()
                .replaceId("RID")
                // writing phone number is not allowed, so it was ignored
                .bodyEquals(1, "{\"id\":RID,\"name\":\"zzz\",\"phoneNumber\":null}");

        tester.e3().matcher().assertOneMatch();
        tester.e3().matcher().eq("phone_number", null).assertOneMatch();
    }

    @Test
    public void testReadConstraints1() {

        tester.target("/e3/constrained")
                .post("{\"name\":\"zzz\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"name\":\"zzz\"}");
    }

    @Test
    public void testInclude_ReadConstraints() {

        // writing "phoneNumber" is allowed, but reading is not ... must be in DB, but not in response

        tester.target("/e3/constrained")
                .queryParam("include", "name")
                .queryParam("include", "phoneNumber")
                .post("{\"name\":\"zzz\",\"phoneNumber\":\"123456\"}")
                .wasCreated()
                .bodyEquals(1, "{\"name\":\"zzz\"}");

        tester.e3().matcher().assertOneMatch();
        tester.e3().matcher().eq("name", "zzz").eq("phone_number", "123456").assertOneMatch();
    }

    @Test
    public void testReadConstraints_DisallowRelated() {

        tester.target("/e3/constrained")
                .queryParam("include", E3.E2.getName())
                .post("{\"name\":\"zzz\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"name\":\"zzz\"}");
    }


    @Test
    public void testToOne() {

        tester.e2().insertColumns("id_", "name")
                .values(1, "xxx")
                .values(8, "yyy").exec();

        tester.target("/e3")
                .post("{\"e2\":8,\"name\":\"MM\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"name\":\"MM\",\"phoneNumber\":null}");

        tester.e3().matcher().assertOneMatch();
        tester.e3().matcher().eq("e2_id", 8).eq("name", "MM").assertOneMatch();
    }

    @Test
    public void testToOne_Null() {

        tester.target("/e3")
                .post("{\"e2_id\":null,\"name\":\"MM\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"name\":\"MM\",\"phoneNumber\":null}");

        tester.e3().matcher().assertOneMatch();
        tester.e3().matcher().eq("e2_id", null).assertOneMatch();
    }

    @Test
    public void testToOne_BadFK() {

        tester.target("/e3")
                .post("{\"e2\":15,\"name\":\"MM\"}")
                .wasNotFound()
                .bodyEquals("{\"success\":false,\"message\":\"Related object 'E2' with ID '[15]' is not found\"}");

        tester.e3().matcher().assertNoMatches();
    }

    @Test
    public void testBulk() {

        tester.target("/e3/")
                .queryParam("exclude", "id")
                .queryParam("include", E3.NAME.getName())
                .post("[{\"name\":\"aaa\"},{\"name\":\"zzz\"},{\"name\":\"bbb\"},{\"name\":\"yyy\"}]")
                .wasCreated()
                // ordering from request must be preserved...
                .bodyEquals(4, "{\"name\":\"aaa\"},{\"name\":\"zzz\"},{\"name\":\"bbb\"},{\"name\":\"yyy\"}");
    }

    @Test
    public void testToMany() {

        tester.e3().insertColumns("id_", "name")
                .values(1, "xxx")
                .values(8, "yyy").exec();

        Long id = tester.target("/e2")
                .queryParam("include", E2.E3S.getName())
                .queryParam("exclude", E2.ADDRESS.getName(), E2.E3S.dot(E3.NAME).getName(), E2.E3S.dot(E3.PHONE_NUMBER).getName())
                .post("{\"e3s\":[1,8],\"name\":\"MM\"}")
                .wasCreated()
                .replaceId("RID")
                .bodyEquals(1, "{\"id\":RID,\"e3s\":[{\"id\":1},{\"id\":8}],\"name\":\"MM\"}")
                .getId();

        assertNotNull(id);

        tester.e3().matcher().eq("e2_id", id).assertMatches(2);
    }

    @Test
    public void testByteArrayProperty() {

        String base64Encoded = "c29tZVZhbHVlMTIz"; // someValue123

        tester.target("/e19")
                .queryParam("include", E19.GUID.getName())
                .post("{\"guid\":\"" + base64Encoded + "\"}")
                .wasCreated()
                .bodyEquals(1, "{\"guid\":\"" + base64Encoded + "\"}");
    }

    @Test
    public void testFloatProperty() {
        tester.target("/e19/float")
                .queryParam("include", "floatObject", "floatPrimitive")
                .post("{\"floatObject\":1.0,\"floatPrimitive\":2.0}")
                .wasCreated()
                .bodyEquals(1, "{\"floatObject\":1.0,\"floatPrimitive\":2.0}");
        tester.e19().matcher().eq("float_object", 1.0).eq("float_primitive", 2.0).assertOneMatch();
    }

    @Test
    public void testFloatProperty_FromInt() {
        tester.target("/e19/float")
                .queryParam("include", "floatObject", "floatPrimitive")
                .post("{\"floatObject\":1,\"floatPrimitive\":2}")
                .wasCreated()
                .bodyEquals(1, "{\"floatObject\":1.0,\"floatPrimitive\":2.0}");
        tester.e19().matcher().eq("float_object", 1.0).eq("float_primitive", 2.0).assertOneMatch();
    }

    @Test
    public void testDoubleProperty() {
        tester.target("/e19/double").post("{\"doubleObject\":1.0,\"doublePrimitive\":2.0}").wasCreated();
        tester.e19().matcher().eq("double_object", 1.0).eq("double_primitive", 2.0).assertOneMatch();
    }

    @Test
    public void testDoubleProperty_FromInt() {
        tester.target("/e19/double").post("{\"doubleObject\":1,\"doublePrimitive\":2}").wasCreated();
        tester.e19().matcher().eq("double_object", 1.0).eq("double_primitive", 2.0).assertOneMatch();
    }

    @Path("")
    public static class Resource {

        @Context
        private Configuration config;

        @POST
        @Path("e2")
        public DataResponse<E2> createE2(String targetData, @Context UriInfo uriInfo) {
            return Ag.create(E2.class, config).uri(uriInfo).syncAndSelect(targetData);
        }

        @POST
        @Path("e3")
        public DataResponse<E3> create(@Context UriInfo uriInfo, String requestBody) {
            return Ag.create(E3.class, config).uri(uriInfo).syncAndSelect(requestBody);
        }

        @POST
        @Path("e3/constrained")
        public DataResponse<E3> insertE3ReadConstrained(@Context UriInfo uriInfo, String requestBody) {
            Constraint<E3> tc = Constraint.idOnly(E3.class).attribute(E3.NAME.getName());
            return Ag.create(E3.class, config).uri(uriInfo).readConstraint(tc).syncAndSelect(requestBody);
        }

        @POST
        @Path("e3/w/constrained")
        public DataResponse<E3> insertE3WriteConstrained(@Context UriInfo uriInfo, String requestBody) {
            Constraint<E3> tc = Constraint.idOnly(E3.class).attribute(E3.NAME.getName());
            return Ag.create(E3.class, config).uri(uriInfo).writeConstraint(tc).syncAndSelect(requestBody);
        }

        @POST
        @Path("e4")
        public DataResponse<E4> createE4(String requestBody) {
            return Ag.create(E4.class, config).syncAndSelect(requestBody);
        }

        @POST
        @Path("e4/sync")
        public SimpleResponse createE4_DefaultData(String requestBody) {
            return Ag.create(E4.class, config).sync(requestBody);
        }

        @POST
        @Path("e8/w/constrainedid/{id}")
        public SimpleResponse create_WriteConstrainedId(
                @PathParam("id") int id,
                @Context UriInfo uriInfo,
                String requestBody) {

            Constraint<E8> tc = Constraint.idOnly(E8.class).attribute(E8.NAME.getName());
            return Ag.create(E8.class, config).uri(uriInfo).id(id).writeConstraint(tc).sync(requestBody);
        }

        @POST
        @Path("e8/w/constrainedidblocked/{id}")
        public SimpleResponse create_WriteConstrainedIdBlocked(
                @PathParam("id") int id,
                @Context UriInfo uriInfo,
                String requestBody) {
            Constraint<E8> tc = Constraint.excludeAll(E8.class).attribute(E8.NAME.getName());
            return Ag.create(E8.class, config).uri(uriInfo).id(id).writeConstraint(tc).sync(requestBody);
        }

        @POST
        @Path("e16")
        public DataResponse<E16> createE16(String requestBody) {
            return Ag.create(E16.class, config).syncAndSelect(requestBody);
        }

        @POST
        @Path("e17")
        public DataResponse<E17> createE17(
                @Context UriInfo uriInfo,
                @QueryParam("id1") Integer id1,
                @QueryParam("id2") Integer id2,
                String requestBody) {

            Map<String, Object> ids = new HashMap<>();
            ids.put(E17.ID1_PK_COLUMN, id1);
            ids.put(E17.ID2_PK_COLUMN, id2);

            return Ag.create(E17.class, config).id(ids).syncAndSelect(requestBody);
        }

        @POST
        @Path("e19")
        public DataResponse<E19> createE19(@Context UriInfo uriInfo, String data) {
            return Ag.create(E19.class, config).uri(uriInfo).syncAndSelect(data);
        }

        @POST
        @Path("e19/float")
        public DataResponse<E19> createE19_FloatAttribute(@Context UriInfo uriInfo, String data) {
            return Ag.create(E19.class, config).uri(uriInfo).syncAndSelect(data);
        }

        @POST
        @Path("e19/double")
        public SimpleResponse create_E19_DoubleAttribute(String entityData) {
            return Ag.create(E19.class, config).sync(entityData);
        }
    }
}
