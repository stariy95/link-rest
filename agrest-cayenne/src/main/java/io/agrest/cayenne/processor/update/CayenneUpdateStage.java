package io.agrest.cayenne.processor.update;

import io.agrest.*;
import io.agrest.cayenne.persister.ICayennePersister;
import io.agrest.cayenne.processor.CayenneProcessor;
import io.agrest.meta.AgAttribute;
import io.agrest.meta.AgDataMap;
import io.agrest.meta.AgIdPart;
import io.agrest.meta.AgRelationship;
import io.agrest.runtime.processor.update.ByIdObjectMapperFactory;
import io.agrest.runtime.processor.update.UpdateContext;
import org.apache.cayenne.DataObject;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.exp.property.Property;
import org.apache.cayenne.exp.property.PropertyFactory;
import org.apache.cayenne.map.ObjRelationship;
import org.apache.cayenne.query.SelectQuery;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * @since 2.7
 */
public class CayenneUpdateStage extends CayenneMergeChangesStage {

    public CayenneUpdateStage(
            @Inject AgDataMap dataMap,
            @Inject ICayennePersister persister) {

        super(dataMap, persister.entityResolver());
    }

    @Override
    protected <T extends DataObject> void sync(UpdateContext<T> context) {

        ObjectMapper<T> mapper = createObjectMapper(context);

        Map<Object, Collection<EntityUpdate<T>>> updatesByKey = mutableUpdatesByKey(context, mapper);

        // find existing objects and merge values into them
        for (T o : existingObjects(context, updatesByKey.keySet(), mapper)) {
            Object key = mapper.keyForObject(o);

            Collection<EntityUpdate<T>> updates = updatesByKey.remove(key);

            // a null can only mean some algorithm malfunction
            if (updates == null) {
                throw new AgException(Response.Status.INTERNAL_SERVER_ERROR, "Invalid key item: " + key);
            }

            updateSingle(context, o, updates);
        }

        // check leftovers - those correspond to objects missing in the DB or
        // objects with no keys
        afterUpdatesMerge(context, updatesByKey);
    }

    protected <T extends DataObject> void afterUpdatesMerge(UpdateContext<T> context, Map<Object, Collection<EntityUpdate<T>>> keyMap) {
        if (!keyMap.isEmpty()) {
            Object firstKey = keyMap.keySet().iterator().next();

            if (firstKey == null) {
                throw new AgException(Response.Status.BAD_REQUEST, "Can't update. No id for object");
            }

            throw new AgException(Response.Status.NOT_FOUND,
                    "No object for ID '" + firstKey + "' and entity '" + context.getEntity().getName() + "'");
        }
    }

    protected <T extends DataObject> Map<Object, Collection<EntityUpdate<T>>> mutableUpdatesByKey(
            UpdateContext<T> context,
            ObjectMapper<T> mapper) {

        Collection<EntityUpdate<T>> updates = context.getUpdates();

        // sizing the map with one-update per key assumption
        Map<Object, Collection<EntityUpdate<T>>> map = new HashMap<>((int) (updates.size() / 0.75));

        for (EntityUpdate<T> u : updates) {
            Object key = mapper.keyForUpdate(u);

            // The key can be "null", and the update may still be valid. It means it won't match anything in the
            // DB though, and the request can not be idempotent...

            map.computeIfAbsent(key, k -> new ArrayList<>(2)).add(u);
        }

        return map;
    }

    protected <T extends DataObject> ObjectMapper<T> createObjectMapper(UpdateContext<T> context) {
        ObjectMapperFactory mapper = context.getMapper() != null
                ? context.getMapper()
                : ByIdObjectMapperFactory.mapper();
        return mapper.createMapper(context);
    }

    <T extends DataObject> List<T> existingObjects(UpdateContext<T> context, Collection<Object> keys, ObjectMapper<T> mapper) {

        // TODO: split query in batches:
        // respect Constants.SERVER_MAX_ID_QUALIFIER_SIZE_PROPERTY
        // property of Cayenne , breaking query into subqueries.
        // Otherwise this operation will not scale.. Though I guess since we are
        // not using streaming API to read data from Cayenne, we are already
        // limited in how much data can fit in the memory map.

        List<Expression> expressions = new ArrayList<>(keys.size());
        for (Object key : keys) {

            // update keys can be null... see a note in "mutableUpdatesByKey"
            if (key != null) {
                Expression e = mapper.expressionForKey(key);
                if (e != null) {
                    expressions.add(e);
                }
            }
        }

        // no keys or all keys were for non-persistent objects
        if (expressions.isEmpty()) {
            return Collections.emptyList();
        }

        ResourceEntity resourceEntity = context.getEntity();
        buildQuery(context, context.getEntity(), ExpressionFactory.joinExp(Expression.OR, expressions));

        List<T> objects = fetchEntity(context, resourceEntity);
        if (context.isById() && objects.size() > 1) {
            throw new AgException(Response.Status.INTERNAL_SERVER_ERROR, String.format(
                    "Found more than one object for ID '%s' and entity '%s'",
                    context.getId(), context.getEntity().getName()));
        }

        return objects;
    }


    <T> SelectQuery<T> buildQuery(UpdateContext<T> context, ResourceEntity<T> entity, Expression qualifier) {

        SelectQuery<T> query = SelectQuery.query(entity.getType());

        if (qualifier != null) {
            query.setQualifier(qualifier);
        }

        CayenneProcessor.setQuery(entity, query);
        buildChildrenQuery(context, entity, entity.getChildren());

        return query;
    }

    protected void buildChildrenQuery(UpdateContext context, ResourceEntity<?> entity, Map<String, NestedResourceEntity<?>> children) {
        if (!children.isEmpty()) {

            SelectQuery<?> parentSelect = CayenneProcessor.getQuery(entity);

            for (Map.Entry<String, NestedResourceEntity<?>> e : children.entrySet()) {
                NestedResourceEntity child = e.getValue();

                if (entityResolver.getObjEntity(child.getType()) == null) {
                    continue;
                }

                List<Property> properties = new ArrayList<>();
                properties.add(PropertyFactory.createSelf(child.getType()));

                ObjRelationship objRelationship = objRelationshipForIncomingRelationship(child);

                for (AgIdPart id : entity.getAgEntity().getIdParts()) {
                    properties.add(PropertyFactory.createBase(ExpressionFactory.dbPathExp(
                            objRelationship.getReverseDbRelationshipPath() + "." + id.getName()),
                            id.getType()));
                }

                SelectQuery childQuery = buildQuery(context, child, translateExpressionToSource(objRelationship, parentSelect.getQualifier()));
                childQuery.setColumns(properties);
            }
        }
    }


    protected <T> List<T> fetchEntity(UpdateContext<T> context, ResourceEntity<T> resourceEntity) {

        SelectQuery<T> select = CayenneProcessor.getQuery(resourceEntity);
        List<T> objects = CayenneUpdateStartStage.cayenneContext(context).select(select);
        fetchChildren(context, resourceEntity, resourceEntity.getChildren());

        return objects;
    }

    protected <T> void fetchChildren(UpdateContext context, ResourceEntity<T> parent, Map<String, NestedResourceEntity<?>> children) {
        if (!children.isEmpty()) {
            for (Map.Entry<String, NestedResourceEntity<?>> e : children.entrySet()) {
                NestedResourceEntity childEntity = e.getValue();

                List childObjects = fetchEntity(context, childEntity);

                AgRelationship rel = parent.getChild(e.getKey()).getIncoming();

                assignChildrenToParent(
                        parent,
                        childObjects,
                        rel.isToMany()
                                ? (i, o) -> childEntity.addToManyResult(i, o)
                                : (i, o) -> childEntity.setToOneResult(i, o));
            }
        }
    }

    /**
     * Assigns child items to the appropriate parent item
     */
    protected <T> void assignChildrenToParent(ResourceEntity<T> parentEntity, List children, BiConsumer<AgObjectId, Object> resultKeeper) {
        // saves a result
        for (Object child : children) {
            if (child instanceof Object[]) {
                Object[] ids = (Object[]) child;
                if (ids.length == 2) {
                    resultKeeper.accept(new SimpleObjectId(ids[1]), (T) ids[0]);
                } else if (ids.length > 2) {
                    // saves entity with a compound ID
                    Map<String, Object> compoundKeys = new LinkedHashMap<>();
                    AgAttribute[] idAttributes = parentEntity.getAgEntity().getIdParts().toArray(new AgAttribute[0]);
                    if (idAttributes.length == (ids.length - 1)) {
                        for (int i = 1; i < ids.length; i++) {
                            compoundKeys.put(idAttributes[i - 1].getName(), ids[i]);
                        }
                    }
                    resultKeeper.accept(new CompoundObjectId(compoundKeys), (T) ids[0]);
                }
            }
        }
    }

    // TODO: copied verbatim from CayenneQueryAssembler... Unify this code?
    protected ObjRelationship objRelationshipForIncomingRelationship(NestedResourceEntity<?> entity) {
        return entityResolver.getObjEntity(entity.getParent().getName()).getRelationship(entity.getIncoming().getName());
    }

    // TODO: copied verbatim from CayenneQueryAssembler... Unify this code?
    protected Expression translateExpressionToSource(ObjRelationship relationship, Expression expression) {
        return expression != null
                ? relationship.getSourceEntity().translateToRelatedEntity(expression, relationship.getName())
                : null;
    }
}
