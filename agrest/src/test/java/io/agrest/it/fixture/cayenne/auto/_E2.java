package io.agrest.it.fixture.cayenne.auto;

import java.util.List;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.exp.Property;

import io.agrest.it.fixture.cayenne.E3;

/**
 * Class _E2 was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _E2 extends CayenneDataObject {

    private static final long serialVersionUID = 1L; 

    public static final String ID_PK_COLUMN = "id";

    public static final Property<String> ADDRESS = Property.create("address", String.class);
    public static final Property<String> NAME = Property.create("name", String.class);
    public static final Property<List<E3>> E3S = Property.create("e3s", List.class);

    public void setAddress(String address) {
        writeProperty("address", address);
    }
    public String getAddress() {
        return (String)readProperty("address");
    }

    public void setName(String name) {
        writeProperty("name", name);
    }
    public String getName() {
        return (String)readProperty("name");
    }

    public void addToE3s(E3 obj) {
        addToManyTarget("e3s", obj, true);
    }
    public void removeFromE3s(E3 obj) {
        removeToManyTarget("e3s", obj, true);
    }
    @SuppressWarnings("unchecked")
    public List<E3> getE3s() {
        return (List<E3>)readProperty("e3s");
    }


}
