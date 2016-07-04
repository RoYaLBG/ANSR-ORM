package bg.royal.orm.operation;

import bg.royal.orm.DbContext;
import bg.royal.orm.collections.LazyResultSet;
import bg.royal.orm.persistence.Column;
import bg.royal.orm.persistence.Entity;
import bg.royal.orm.persistence.Id;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Ivan Yonkov
 */
public class EntityManager implements DbContext {

    private Connection connection;

    public EntityManager(Connection connection) {
        this.connection = connection;
    }

    public <E> boolean persist(E entity) throws IllegalAccessException, SQLException {
        Field primary = this.getId(entity.getClass());
        primary.setAccessible(true);
        Object value = primary.get(entity);
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

            query += "`" + this.getFieldName(field) + "` = ";
            query += "'" + field.get(entity).toString() + "'";

            if (i < fields.length - 1) {
                query += ", ";
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
