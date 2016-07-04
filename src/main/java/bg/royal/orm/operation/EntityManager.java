package bg.royal.orm.operation;

import bg.royal.orm.DbContext;
import bg.royal.orm.RelationalResultSet;
import bg.royal.orm.collections.LazyResultSet;
import bg.royal.orm.persistence.Column;
import bg.royal.orm.persistence.Entity;
import bg.royal.orm.persistence.Id;
import bg.royal.orm.persistence.relation.Join;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author Ivan Yonkov
 */
public class EntityManager implements DbContext {

    private Connection connection;
    private Set<Object> persistedEntities;

    public EntityManager(Connection connection) {
        this.connection = connection;
        this.persistedEntities = new HashSet<>();
    }

    public <E> boolean persist(E entity) throws SQLException, IllegalAccessException {
        return this.persist(entity, false);
    }

    public <E> boolean persist(E entity, boolean withRelations) throws IllegalAccessException, SQLException {
        if (entity == null || this.persistedEntities.contains(entity)) {
            return false;
        }

        if (withRelations) {
            this.persistedEntities.add(entity);
            for (Field field : entity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Join.class)) {
                    Join annotation = field.getAnnotation(Join.class);
                    Class relation = annotation.table();
                    field.setAccessible(true);
                    Object relatedEntity = field.get(entity);
                    if (relatedEntity instanceof Iterable) {
                        for (Object element : (Iterable)relatedEntity) {
                            this.persist(element, true);
                        }
                    } else {
                        this.persist(relatedEntity, true);
                    }
                }
            }
        }


        Field primary = this.getId(entity.getClass());
        primary.setAccessible(true);
        Object value = primary.get(entity);
        this.persistedEntities = new HashSet<>();
        if (value == null || (Long)value <= 0) {
            return this.doInsert(entity, primary);
        }

        return this.doUpdate(entity, primary);
    }

    public <E> Iterable<E> createQuery(Class<E> table, String query) throws SQLException {
        Statement stmt = this.connection.createStatement();
        ResultSet rs = stmt.executeQuery(query);

        return this.createLazyResultSet(table, rs);
    }

    public <E> Iterable<E> find(Class<E> table, String where) throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        Statement stmt = this.connection.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * FROM " + this.getTableName(table) + " WHERE 1 " + (where != null ? "AND " + where : ""));

        return this.createLazyResultSet(table, rs);
    }

    public <E> RelationalResultSet<E> compose(Class<E> table, String where) throws SQLException {
        List<String> fields =
                Arrays.stream(table.getDeclaredFields())
                        .filter(f -> f.getType() != Iterable.class)
                        .map(f -> this.getTableName(table) + "." + this.getFieldName(f) + " AS " + this.getTableName(table) + this.getFieldName(f))
                        .collect(Collectors.toList());



        String query = "FROM " + this.getTableName(table);
        where = " WHERE 1 " + (where != null ? "AND " + where : "");

        return new MiddleResultSet<>(this.connection, query, where, this, table, fields);
    }

    public <E> Iterable<E> find(Class<E> table) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException {
        return this.find(table, null);
    }

    public <E> E findFirst(Class<E> table, String where) throws SQLException, IllegalAccessException, InstantiationException {
        Statement stmt = this.connection.createStatement();
        String query = "SELECT * FROM " + this.getTableName(table) + " WHERE 1 " + (where != null ? "AND " + where : "") + " LIMIT 1";
        ResultSet rs = stmt.executeQuery(query);
        E entity = table.newInstance();
        rs.next();
        this.fillEntity(table, rs, entity);

        return entity;
    }

    public <E> E findOne(Class<E> table, Object value) throws IllegalAccessException, SQLException, InstantiationException {
        Field primary = this.getId(table);
        String where = this.getFieldName(primary) + " = '" + value.toString() + "'";

        return this.findFirst(table, where);
    }

    public <E> E findFirst(Class<E> table) throws IllegalAccessException, SQLException, InstantiationException {
        return this.findFirst(table, null);
    }

    private <E> boolean doInsert(E entity, Field primary) throws IllegalAccessException, SQLException {
        String columns = "";
        String values = "";

        Field[] fields =
                Arrays.stream(entity.getClass().getDeclaredFields())
                        .filter(f -> f.getType() != Iterable.class)
                        .toArray(Field[]::new);


        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];

            if (field.getName().equals(primary.getName())) continue;

            field.setAccessible(true);
            columns += "`" + this.getFieldName(field) + "`";

            values+="'" + (field.get(entity).toString()) + "'";

            if (i < fields.length - 1) {
                columns += ", ";
                values += ", ";
            }
        }


        String query = "INSERT INTO " + this.getTableName(entity.getClass()) + " ";
        query += "(" + columns + ")";
        query += " VALUES (" + values + ");";

        return this.connection.prepareStatement(query).execute();
    }

    private <E> boolean doUpdate(E entity, Field primary) throws IllegalAccessException, SQLException {
        String query = "UPDATE " + this.getTableName(entity.getClass()) + " SET ";
        String where = " WHERE 1";
        Field[] fields =
                Arrays.stream(entity.getClass().getDeclaredFields())
                .filter(f -> f.getType() != Iterable.class)
                .toArray(Field[]::new);

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);
            if (field.getName().equals(primary.getName())) {
                where += " AND ";
                where += "`" + this.getFieldName(field) + "` = ";
                where += "'" + field.get(entity).toString() + "';";
                continue;
            }


            if (field.get(entity) != null) {
                query += "`" + this.getFieldName(field) + "` = ";
                field.setAccessible(true);
                String currentValue = field.get(entity).toString();
                Optional<Field> relativeIdField =
                        Arrays.stream(field.get(entity).getClass().getDeclaredFields())
                        .filter(t -> t.isAnnotationPresent(Id.class))
                        .findFirst();
                if (relativeIdField.isPresent()) {
                    Field relativePrimary = relativeIdField.get();
                    relativePrimary.setAccessible(true);
                    Object val = relativePrimary.get(field.get(entity));
                    if (val != null) {
                        currentValue = val.toString();
                    } else {
                        currentValue = "";
                    }
                }
                query += "'" + currentValue  + "'";
                if (i < fields.length - 1) {
                    query += ", ";
                }
            }



        }

        query += where;

        return this.connection.prepareStatement(query).execute();
    }

    private Field getId(Class c) {
        return Arrays.stream(c.getDeclaredFields()).filter(f ->
                f.isAnnotationPresent(Id.class))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Cannot operate with entity without primary key"));
    }

    private <E> String getTableName(Class<E> entity) {
        String tableName = "";
        if (entity.isAnnotationPresent(Entity.class)) {
            Entity annotation = entity.getAnnotation(Entity.class);
            tableName = annotation.name();
        }

        if (tableName.equals(""))  {
            tableName = entity.getSimpleName();
        }

        return tableName;
    }

    private String getFieldName(Field field) {
        String fieldName = "";
        if (field.isAnnotationPresent(Column.class)) {
            Column annotation = field.getAnnotation(Column.class);
            fieldName = annotation.name();
        }

        if (fieldName.equals(""))  {
            fieldName = field.getName();
        }

        return fieldName;
    }

    private <E> void fillEntity(Class<E> table, ResultSet rs, E entity) throws IllegalAccessException, SQLException {
        for (int i = 0; i < table.getDeclaredFields().length; i++) {
            Field field = table.getDeclaredFields()[i];
            field.setAccessible(true);
            String name = this.getFieldName(field);
            EntityFiller.fillField(field, entity, rs, name);
        }
    }

    private <E> LazyResultSet<E> createLazyResultSet(Class<E> table, ResultSet rs) {
        Supplier<E> supplier = () -> {
            try {
                if (rs.next()) {
                    E entity = table.newInstance();

                    this.fillEntity(table, rs, entity);

                    return entity;
                }
            } catch (SQLException | InstantiationException | IllegalAccessException e) {
                return null;
            }

            return null;
        };

        return new LazyResultSet<>(supplier);
    }


    private static class MiddleResultSet<T> implements RelationalResultSet<T> {

        private Connection connection;
        private String query;
        private String where;
        private EntityManager entityManager;
        private Class<T> entity;
        private List<String> columns;
        private Set<String> classNames;
        private Map<String, Set<String>> joins;

        MiddleResultSet(Connection connection, String query, String where, EntityManager em, Class<T> entity, List<String> columns) {
            this.connection = connection;
            this.query = query;
            this.where = where;
            this.entityManager = em;
            this.entity = entity;
            this.columns = columns;
            this.classNames = new HashSet<>();
            this.joins = new HashMap<>();
        }

        MiddleResultSet(Connection connection, String query, String where, EntityManager em, Class<T> entity, List<String> columns, Set<String> classNames, Map<String, Set<String>> joins) {
            this(connection, query, where, em, entity, columns);
            this.classNames = classNames;
            this.joins = joins;
        }

        public <E> RelationalResultSet<T> innerJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.join(joinedEntity, bg.royal.orm.criteria.Join.INNER, this.entity);
        }

        public <E> RelationalResultSet<T> leftJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.join(joinedEntity, bg.royal.orm.criteria.Join.LEFT, this.entity);
        }

        public <E> RelationalResultSet<T> rightJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.join(joinedEntity, bg.royal.orm.criteria.Join.RIGHT, this.entity);
        }

        public <E> RelationalResultSet<E> deepInnerJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.deepJoin(
                    joinedEntity,
                    bg.royal.orm.criteria.Join.INNER
            );
        }

        public <E> RelationalResultSet<E> deepLeftJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.deepJoin(
                    joinedEntity,
                    bg.royal.orm.criteria.Join.LEFT
            );
        }

        public <E> RelationalResultSet<E> deepRightJoin(Class<E> joinedEntity) throws IllegalAccessException {
            return this.deepJoin(
                    joinedEntity,
                    bg.royal.orm.criteria.Join.RIGHT
            );
        }

        public <E> RelationalResultSet<E> toEntity(Class<E> from) {
            return new MiddleResultSet<>(
                    this.connection,
                    this.query,
                    this.where,
                    this.entityManager,
                    from,
                    this.columns,
                    this.classNames,
                    this.joins
            );
        }

        public Iterable<T> get() throws SQLException {
            Statement stmt = this.connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT " +
                    String.join(", ", this.columns) + " " +
                    this.query + this.where);

            Supplier<T> supplier = () -> {
                try {
                    boolean hasNext = rs.next();
                    if (hasNext) {
                        long id = rs.getLong(
                                this.entityManager.getTableName(this.entity)
                                        +
                                        this.entityManager.getFieldName(
                                                this.entityManager.getId(this.entity)
                                        )
                        );
                        T entity = this.entity.newInstance();

                        this.fillEntity(this.entity, rs, entity);

                        hasNext = rs.next();
                        if (!hasNext) {
                            return entity;
                        }
                        long newId = rs.getLong(
                                this.entityManager.getTableName(this.entity)
                                        +
                                        this.entityManager.getFieldName(
                                                this.entityManager.getId(this.entity)
                                        )
                        );

                        while (hasNext && newId == id) {
                            this.fillEntity(this.entity, rs, entity);
                            hasNext = rs.next();
                            if (!hasNext)  {
                                newId = -1;
                            } else {
                                newId = rs.getLong(
                                        this.entityManager.getTableName(this.entity)
                                                +
                                                this.entityManager.getFieldName(
                                                        this.entityManager.getId(this.entity)
                                                )
                                );
                            }
                        }



                        return entity;
                    }
                } catch (SQLException | InstantiationException | IllegalAccessException e) {
                    return null;
                }

                return null;
            };

            return new LazyResultSet<>(supplier);
        }

        private <E> MiddleResultSet<E> deepJoin(Class<E> joinedEntity, bg.royal.orm.criteria.Join joinType) throws IllegalAccessException {
            return this.join(
                    joinedEntity,
                    joinType,
                    joinedEntity
            );
        }

        private <E, R> MiddleResultSet<R> join(Class<E> joinedEntity, bg.royal.orm.criteria.Join joinType, Class<R> resultEntity) throws IllegalAccessException {
            Field fieldFrom = this.getRelationalField(this.entity, joinedEntity);
            String referencedTable = this.entityManager.getTableName(joinedEntity);
            String thisTable = this.entityManager.getTableName(this.entity);

            List<String> fields = new ArrayList<>(this.columns);
            fields.addAll(
                    Arrays.stream(joinedEntity.getDeclaredFields())
                            .filter(f -> f.getType() != Iterable.class)
                            .map(f -> referencedTable + "." + this.entityManager.getFieldName(f) + " AS " + referencedTable + this.entityManager.getFieldName(f))
                            .collect(Collectors.toList())
            );

            this.classNames.add(joinedEntity.getName());
            if (!this.joins.containsKey(this.entity.getName())) {
                this.joins.put(this.entity.getName(), new HashSet<>());
            }
            this.joins.get(this.entity.getName()).add(joinedEntity.getName());

            if (fieldFrom.getType() == Iterable.class) {
                return new MiddleResultSet<>(
                        this.connection,
                        this.query + this.createJoinClauseFromIterable(joinType, thisTable, referencedTable, joinedEntity),
                        this.where,
                        this.entityManager,
                        resultEntity,
                        fields,
                        this.classNames,
                        this.joins
                );
            }

            return new MiddleResultSet<>(
                    this.connection,
                    this.query + this.createJoinClauseFromSingleEntity(
                            joinType,
                            thisTable,
                            referencedTable,
                            this.entityManager.getFieldName(fieldFrom),
                            this.entityManager.getFieldName(
                                    this.entityManager.getId(joinedEntity)
                            )
                    ),
                    this.where,
                    this.entityManager,
                    resultEntity,
                    fields,
                    this.classNames,
                    this.joins
            );
        }

        private <I, R> Field getRelationalField(Class<I> from, Class<R> to) {
            return Arrays.stream(from.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(Join.class) && f.getAnnotation(Join.class).table() == to)
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("Cannot join with non-relational entity"));
        }

        private String createJoinClauseFromIterable(bg.royal.orm.criteria.Join joinType, String sourceTable, String referencedTable, Class referencedEntity) {
            String query = " ";

            query += joinType.toString() + " JOIN " + referencedTable;
            query += " ON " + sourceTable + "." + this.entityManager.getFieldName(this.entityManager.getId(this.entity));
            query += " = " + referencedTable + ".";

            Field fieldTo = this.getRelationalField(referencedEntity, this.entity);

            query += this.entityManager.getFieldName(fieldTo);

            return query;
        }

        private String createJoinClauseFromSingleEntity(bg.royal.orm.criteria.Join joinType, String sourceTable, String referencedTable, String sourceColumn, String referencedColumn) {
            String query = " ";

            query += joinType.toString() + " JOIN " + referencedTable;
            query += " ON " + sourceTable + "." + sourceColumn;
            query += " = " + referencedTable + ".";
            query += referencedColumn;

            return query;
        }

        public void printQuery() {
            System.out.println("SELECT " +
                    String.join(", ", this.columns) + " " +
                    this.query + this.where);
        }

        private <E> void fillEntity(Class<? extends E> table, ResultSet rs, E entity) throws IllegalAccessException, SQLException, InstantiationException {
            for (int i = 0; i < table.getDeclaredFields().length; i++) {
                Field field = table.getDeclaredFields()[i];
                field.setAccessible(true);
                String name = this.entityManager.getTableName(table) + this.entityManager.getFieldName(field);
                boolean hasAnnotation = field.isAnnotationPresent(Join.class);
                Join annotation = null;
                String key = null;
                String value = null;
                if (hasAnnotation) {
                    annotation = field.getAnnotation(Join.class);
                    key = entity.getClass().getName();
                    value = annotation.table().getName();
                }
                if (hasAnnotation
                        && this.joins.containsKey(key)
                        && this.joins.get(key).contains(value)) {
                    Join joinAnnotation = field.getAnnotation(Join.class);
                    Class collectionType = joinAnnotation.table();
                    Collection<Object> collection =
                            (Collection<Object>) field.get(entity);
                    Object instance = collectionType.newInstance();

                    boolean toAdd = true;
                    for (Field f : collectionType.getDeclaredFields()) {
                       if (f.isAnnotationPresent(Join.class) &&
                                f.getType() != Iterable.class &&
                                f.getAnnotation(Join.class).table() == entity.getClass()
                                ) {
                            f.setAccessible(true);
                            f.set(instance, entity);
                            String nestedKey = f.getAnnotation(Join.class).table().getName();
                            if (this.joins.containsKey(nestedKey)) {
                                this.fillEntity(collectionType, rs, instance);
                            }


                        } else {
                            String fieldName =
                                    this.entityManager.getTableName(collectionType)
                                            + this.entityManager.getFieldName(f);
                            EntityFiller.fillField(f, instance, rs, fieldName);
                        }

                        // check if relation already exists in the collection
                        // TODO: Better way for checking
                        // TODO: Bug - if it exists it stops to add relations to this duplicate
                        toAdd = true;
                        if (this.entityManager.getId(collectionType).getName().equals(f.getName())) {
                            for (Object o : collection) {
                                if (!toAdd) break;
                                for (Field innerField : o.getClass().getDeclaredFields()) {
                                    innerField.setAccessible(true);
                                    f.setAccessible(true);
                                    if (f.getName().equals(innerField.getName()) && f.get(instance) == innerField.get(o)) {
                                        toAdd = false;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!toAdd) break;
                    }


                    if(toAdd) {
                        collection.add(instance);
                    }
                } else {
                    EntityFiller.fillField(field, entity, rs, name);
                }
            }
        }
    }
}

class EntityFiller {
    static void fillField(Field field, Object instance, ResultSet rs, String fieldName) throws SQLException, IllegalAccessException {
        field.setAccessible(true);
        if (field.getType() == int.class || field.getType() == Integer.class) {
            field.set(instance, rs.getInt(fieldName));
        } else if (field.getType() == long.class || field.getType() == Long.class) {
            field.set(instance, rs.getLong(fieldName));
        } else if (field.getType() == double.class || field.getType() == Double.class) {
            field.set(instance, rs.getDouble(fieldName));
        } else if (field.getType() == String.class) {
            field.set(instance, rs.getString(fieldName));
        } else if (field.getType() == Date.class) {
            field.set(instance, rs.getDate(fieldName));
        }
    }
}
