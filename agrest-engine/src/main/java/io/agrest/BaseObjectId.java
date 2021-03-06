package io.agrest;

import io.agrest.meta.AgEntity;
import io.agrest.meta.AgIdPart;

import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.Map;

/**
 * @since 1.24
 */
public abstract class BaseObjectId implements AgObjectId {

    @Override
    public Map<String, Object> asMap(AgEntity<?> entity) {

        if (entity == null) {
            throw new AgException(Response.Status.INTERNAL_SERVER_ERROR,
                    "Can't build ID: entity is null");
        }

        Collection<AgIdPart> idAttributes = entity.getIdParts();
        if (idAttributes.size() != size()) {
            throw new AgException(Response.Status.BAD_REQUEST,
                    "Wrong ID size: expected " + idAttributes.size() + ", got: " + size());
        }

        return asMap(idAttributes);
    }

    protected abstract Map<String, Object> asMap(Collection<AgIdPart> idAttributes);
}
