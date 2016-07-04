package bg.royal.orm;

import java.sql.SQLException;

/**
 * @author Ivan Yonkov
 */
public interface DbContext {

    /**
     * Updates/Inserts entity whether it's attached to the context
     *
     * @param entity Entity to be persisted
     * @return Whether it was persisted
     * @throws IllegalAccessException
     * @throws SQLException
     */
    <E> boolean persist(E entity) throws IllegalAccessException, SQLException;

    /**
     * Executes SQL query against a Database and returns an iterable
     * depending on the resultset
     *
     * @param table The type that denotes what iterable to return
     * @param query Plain SQL Query
     * @return Iterable of table's class
     * @throws SQLException
     */
    <E> Iterable<E> createQuery(Class<E> table, String query) throws SQLException;

    /**
     * Executes builtin SQL by given Table and WHERE clause in order to return
     * an iterable depending on the resultset
     *
     * @param table The type that denotes what iterable to return
     * @param where Plain SQL WHERE Clause
     * @return Iterable of table's class
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    <E> Iterable<E> find(Class<E> table, String where) throws SQLException, ClassNotFoundException, IllegalAccessException, InstantiationException;

    /**
     * Composes SQL query into a relational resultset in order to join
     * relational entities to have entity's collections filled
     *
     * @param table The starting table
     * @param where Plain SQL WHERE clause
     * @throws SQLException
     */
    <E> RelationalResultSet<E> compose(Class<E> table, String where) throws SQLException;

    /**
     * Executes builtin SQL by given Table in order to return
     * an iterable depending on the resultset
     *
     * @param table The type that denotes what iterable to return
     * @return Iterable of table's class
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    <E> Iterable<E> find(Class<E> table) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException;

    /**
     * Executes builtin SQL by given Table and WHERE clause in order to return
     * a single result matching the resultset
     *
     * @param table The type that denotes what entity to return
     * @param where Plain SQL WHERE Clause
     * @return An entity of table's class
     * @throws SQLException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    <E> E findFirst(Class<E> table, String where) throws SQLException, IllegalAccessException, InstantiationException;


    /**
     * Executes builtin SQL and WHERE clause by given table in order to find
     * an entity that matches given Primary Key
     *
     * @param table The type that denotes what entity to return
     * @param value The value of the Primary Key
     * @return An entity of table's class
     * @throws SQLException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    <E> E findOne(Class<E> table, Object value) throws IllegalAccessException, SQLException, InstantiationException;


    /**
     * Executes builtin SQL by given Table in order to return
     * a single result matching the resultset
     *
     * @param table The type that denotes what entity to return
     * @return An entity of table's class
     * @throws SQLException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    <E> E findFirst(Class<E> table) throws IllegalAccessException, SQLException, InstantiationException;

}
