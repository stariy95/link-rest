package io.agrest.runtime.entity;

import io.agrest.ResourceEntity;
import io.agrest.base.protocol.Exp;

/**
 * @since 2.13
 */
public class ExpMerger implements IExpMerger {

    @Override
    public void merge(ResourceEntity<?> resourceEntity, Exp exp) {
        if (exp != null) {
            resourceEntity.andQualifier(exp);
        }
    }
}
