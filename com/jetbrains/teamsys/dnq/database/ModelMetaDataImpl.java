package com.jetbrains.teamsys.dnq.database;

import com.jetbrains.teamsys.core.dataStructures.hash.HashMap;
import com.jetbrains.teamsys.database.EntityMetaData;
import com.jetbrains.teamsys.database.ModelMetaData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 */
public class ModelMetaDataImpl implements ModelMetaData {

    private Set<EntityMetaData> entityMetaDatas = new HashSet<EntityMetaData>();    
    private Map<String, EntityMetaData> typeToEntityMetaDatas = null;

    public void setEntityMetaDatas(@NotNull Set<EntityMetaData> entityMetaDatas) {
        this.entityMetaDatas = entityMetaDatas;
        for (EntityMetaData emd: entityMetaDatas) {
            emd.setModelMetaData(this);
        }
        reset();
        update();
    }

    public void addEntityMetaData(@NotNull EntityMetaData emd) {
        entityMetaDatas.add(emd);
        emd.setModelMetaData(this);
        reset();
    }

    private void reset() {
        typeToEntityMetaDatas = null;
    }

    private void update() {
        if (typeToEntityMetaDatas != null) {
            return;
        }

        synchronized (this) {
            if (typeToEntityMetaDatas != null) {
                return;
            }
            typeToEntityMetaDatas = new HashMap<String, EntityMetaData>();

            for (final EntityMetaData emd : entityMetaDatas) {
                ((EntityMetaDataImpl)emd).reset();

                final String type = emd.getType();
                if (typeToEntityMetaDatas.get(type) != null) {
                    throw new IllegalArgumentException("Duplicate entity [" + type + "]");
                }
                typeToEntityMetaDatas.put(type, emd);

            }

            for (final EntityMetaData emd : entityMetaDatas) {
                // add subtype
                final String superType = emd.getSuperType();
                if (superType != null) {
                    final EntityMetaData superEmd = typeToEntityMetaDatas.get(superType);
                    if (superEmd == null) {
                        throw new IllegalArgumentException("No entity metadata for super type [" + superType + "]");
                    }
                    ((EntityMetaDataImpl)superEmd).addSubType(emd.getType());
                }

                // set supertypes
                List<String> thisAndSuperTypes = new ArrayList<String>();
                String t = emd.getType();
                do {
                    thisAndSuperTypes.add(t);
                    t = typeToEntityMetaDatas.get(t).getSuperType();
                } while (t != null);
                emd.setThisAndSuperTypes(thisAndSuperTypes);
            }
        }
    }

    @Nullable
    public EntityMetaData getEntityMetaData(@NotNull String typeName) {
        update();

        EntityMetaData emd = typeToEntityMetaDatas.get(typeName);
        if (emd == null) {
            throw new IllegalArgumentException("Can't find metadata for entity [" + typeName + "]");
        }
        return emd;
    }

    @NotNull
    public Iterable<EntityMetaData> getEntitiesMetaData() {
        update();
        return typeToEntityMetaDatas.values();
    }

}
