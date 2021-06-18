package io.jmix.graphql.datafetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import io.jmix.core.*;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.impl.importexport.EntityImportPlanJsonBuilder;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.metamodel.model.MetaProperty;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.validation.EntityValidationException;
import io.jmix.graphql.NamingUtils;
import io.jmix.graphql.annotation.GraphQLModifier;
import io.jmix.graphql.loader.GraphQLEntityListLoader;
import io.jmix.graphql.loader.GraphQLEntityLoader;
import io.jmix.graphql.loader.GraphQLEntityRemover;
import io.jmix.graphql.loader.GraphQLEntityUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.PersistenceException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EntityMutationDataFetcher {

    private final Logger log = LoggerFactory.getLogger(EntityMutationDataFetcher.class);

    @Autowired
    ResponseBuilder responseBuilder;
    @Autowired
    private Metadata metadata;
    @Autowired
    protected DataManager dataManager;
    @Autowired
    protected EntitySerialization entitySerialization;
    @Autowired
    EntityImportPlanJsonBuilder entityImportPlanJsonBuilder;
    @Autowired
    protected EntityImportExport entityImportExport;
    @Autowired
    protected DataFetcherPlanBuilder dataFetcherPlanBuilder;
    @Autowired
    protected EntityStates entityStates;
    @Autowired
    private EnvironmentUtils environmentUtils;
    @Autowired
    private AccessManager accessManager;
    @Autowired
    private ListableBeanFactory listableBeanFactory;

    private static Map<Class<?>, Object> entityUpdater;

    private static Map<Class<?>, Object> entityRemover;

    private static final String GRAPHQL_ENTITY_REMOVER_METHOD_NAME = GraphQLEntityRemover.class.getDeclaredMethods()[0].getName();

    private static final String GRAPHQL_ENTITIES_UPDATER_METHOD_NAME = GraphQLEntityUpdater.class.getDeclaredMethods()[0].getName();

    // todo batch commit with association not supported now (not transferred from cuba-graphql)
    public DataFetcher<?> upsertEntity(MetaClass metaClass) {
        Map<Class<?>, Object> entityUpdater = getCustomEntityUpdater();
        return environment -> {

            Class<Object> javaClass = metaClass.getJavaClass();
            Map<String, String> input = environment.getArgument(NamingUtils.uncapitalizedSimpleName(javaClass));
            log.debug("upsertEntity: input {}", input);

            String entityJson = new ObjectMapper().writeValueAsString(input);
            log.debug("upsertEntity: json {}", entityJson);

            Object entity = entitySerialization.entityFromJson(entityJson, metaClass);

            EntityImportPlan entityImportPlan = entityImportPlanJsonBuilder.buildFromJson(entityJson, metaClass);
            checkReadOnlyAttributeWrite(metaClass, entityImportPlan, entity);

            Collection<Object> objects;
            try {
                objects = entityImportExport.importEntities(Collections.singletonList(entity), entityImportPlan, true);
            } catch (EntityValidationException ex) {
                throw new GqlEntityValidationException(ex, metaClass);
            } catch (PersistenceException ex) {
                throw new GqlEntityValidationException(ex, "Can't save entity to database");
            } catch (AccessDeniedException ex) {
                throw new GqlEntityValidationException(ex, "Can't save entity to database. Access denied");
            }
            Object mainEntity = getMainEntity(objects, metaClass);

            FetchPlan fetchPlan = dataFetcherPlanBuilder.buildFetchPlan(metaClass.getJavaClass(), environment);
            // reload for response fetch plan, if required
            if (!entityStates.isLoadedWithFetchPlan(mainEntity, fetchPlan)) {
                LoadContext loadContext = new LoadContext(metaClass).setFetchPlan(fetchPlan);
                loadContext.setId(EntityValues.getId(mainEntity));
                if(!entityUpdater.containsKey(metaClass.getJavaClass())) {
                    mainEntity = dataManager.load(loadContext);
                } else {
                    Object bean = entityUpdater.get(metaClass.getJavaClass());
                    Method method = bean.getClass().getDeclaredMethod(GRAPHQL_ENTITIES_UPDATER_METHOD_NAME,
                            LoadContext.class);
                    mainEntity = method.invoke(bean, loadContext);
                }
            }

            return responseBuilder.buildResponse((Entity) mainEntity, fetchPlan, metaClass, environmentUtils.getDotDelimitedProps(environment));
        };
    }

    public DataFetcher<?> deleteEntity(MetaClass metaClass) {
        Map<Class<?>, Object> entityRemover = getCustomEntityUpdater();
        return environment -> {
            try {
                checkCanDeleteEntity(metaClass);
            } catch (PersistenceException ex) {
                throw new GqlEntityValidationException(ex, ex.getMessage());
            }
            // todo support not only UUID types of id
            UUID id = UUID.fromString(environment.getArgument("id"));
            log.debug("deleteEntity: id {}", id);
            Id<?> entityId = Id.of(id, metaClass.getJavaClass());
            if(!entityRemover.containsKey(metaClass.getJavaClass())) {
                dataManager.remove(entityId);
            } else {
                Object bean = entityRemover.get(metaClass.getJavaClass());
                Method method = bean.getClass().getDeclaredMethod(GRAPHQL_ENTITY_REMOVER_METHOD_NAME, Id.class);
                method.invoke(bean, entityId);
            }
            return null;
        };
    }

    protected Object getMainEntity(Collection<Object> importedEntities, MetaClass metaClass) {
        Object mainEntity = null;
        if (importedEntities.size() > 1) {
            Optional<Object> first = importedEntities.stream().filter(e -> metadata.getClass(e).equals(metaClass)).findFirst();
            if (first.isPresent()) mainEntity = first.get();
        } else {
            mainEntity = importedEntities.iterator().next();
        }
        return mainEntity;
    }

    protected void checkCanDeleteEntity(MetaClass metaClass) {
        CrudEntityContext entityContext = new CrudEntityContext(metaClass);
        accessManager.applyRegisteredConstraints(entityContext);
        if (!entityContext.isDeletePermitted()) {
            throw new PersistenceException(
                    String.format("Deletion of the %s is forbidden", metaClass.getName()));
        }
    }

    protected void checkReadOnlyAttributeWrite(MetaClass metaClass, EntityImportPlan importPlan, Object entity) {
        List<String> readOnlyAttributes = importPlan.getProperties().stream()
                .map(property -> metaClass.getProperty(property.getName()))
                .filter(MetaProperty::isReadOnly)
                .map(MetaProperty::getName)
                .collect(Collectors.toList());

        if (!readOnlyAttributes.isEmpty()) {
            throw new GqlEntityValidationException("Modifying read-only attributes is forbidden " + readOnlyAttributes);
        }

        List<String> availableProperties = importPlan.getProperties().stream()
                .map(EntityImportPlanProperty::getName)
                .collect(Collectors.toList());

        Object id = EntityValues.getId(entity);
        List<String> excludedProperties = metaClass.getProperties().stream()
                .filter(metaProperty -> {
                    Object value = EntityValues.getValue(entity, metaProperty.getName());
                    return value != null
                            && !value.equals(id);
                })
                .map(MetaProperty::getName)
                .filter(name -> !availableProperties.contains(name))
                .collect(Collectors.toList());

        if (!excludedProperties.isEmpty()) {
            throw new GqlEntityValidationException("Modifying attributes is forbidden " + excludedProperties);
        }

    }

    protected Map<Class<?>, Object> getCustomEntityRemover() {
        if (entityRemover == null) {
            entityRemover = new HashMap<>();
            Map<String, Object> removers = listableBeanFactory
                    .getBeansWithAnnotation(GraphQLModifier.class);
            for (Object remover : removers.values()) {
                if (remover instanceof GraphQLEntityRemover) {
                    ParameterizedType type = (ParameterizedType) Arrays.stream(remover.getClass().getGenericInterfaces())
                            .filter(t -> t.getTypeName().startsWith(GraphQLEntityRemover.class.getName())).findFirst().get();
                    entityRemover.put((Class<?>) type.getActualTypeArguments()[0], remover);
                }
            }
        }
        return entityRemover;
    }

    protected Map<Class<?>, Object> getCustomEntityUpdater() {
        if (entityUpdater == null) {
            entityUpdater = new HashMap<>();
            Map<String, Object> removers = listableBeanFactory
                    .getBeansWithAnnotation(GraphQLModifier.class);
            for (Object remover : removers.values()) {
                if (remover instanceof GraphQLEntityUpdater) {
                    ParameterizedType type = (ParameterizedType) Arrays.stream(remover.getClass().getGenericInterfaces())
                            .filter(t -> t.getTypeName().startsWith(GraphQLEntityUpdater.class.getName())).findFirst().get();
                    entityUpdater.put((Class<?>) type.getActualTypeArguments()[0], remover);
                }
            }
        }
        return entityUpdater;
    }
}
