package bg.royal.orm;

import java.sql.SQLException;

/**
 * @author Ivan Yonkov
 */
public interface RelationalResultSet<T> {

    <E> RelationalResultSet<T> innerJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<T> leftJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<T> rightJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<E> deepInnerJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<E> deepLeftJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<E> deepRightJoin(Class<E> joinedEntity) throws IllegalAccessException;

    <E> RelationalResultSet<E> toEntity(Class<E> from);

    Iterable<T> get() throws SQLException;

}
