package bg.royal;

import bg.royal.entity.Book;
import bg.royal.entity.Hero;
import bg.royal.entity.User;
import bg.royal.orm.Connector;
import bg.royal.orm.DbContext;
import bg.royal.orm.operation.EntityManager;

import java.sql.*;

/**
 * @author Ivan Yonkov
 */
public class Main {

    public static void main(String[] args) throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        Connector.initConnection("mysql", "root", "", "localhost", "3306", "php-forum");

        DbContext em = new EntityManager(Connector.getConnection());

        // Create new entity (user)
        System.out.println("Creating user");
        User user = new User();
        user.setUsername("nov user");
        user.setPass("123");

        boolean result = em.persist(user);
        System.out.println("User has created: " + result);


        System.out.println("=====================");

        // Get list of entities by condition (users)
        System.out.println("Users with Username Length > 5");
        Iterable<User> usersWhere = em.find(User.class, "LENGTH(username) > 5");
        for (User userResult : usersWhere) {
            System.out.printf("Username: %s | Password: %s%n",
                    userResult.getUsername(),
                    userResult.getPass()
                    );
        }

        System.out.println("=====================");


        // Get list of all entities (users)
        Iterable<User> allUsers = em.find(User.class);
        System.out.println("ALL USERS:");
        for (User userResult : allUsers) {
            System.out.printf("Username: %s | Password: %s%n",
                    userResult.getUsername(),
                    userResult.getPass()
            );
        }


        System.out.println("=====================");


        // Get particular user by primary key
        System.out.println("Find user id=3");
        User userThree = em.findOne(User.class, 3);
        System.out.printf("Username: %s | Password: %s%n",
                userThree.getUsername(),
                userThree.getPass());



        System.out.println("=====================");


        // Update the last found user (with id=3)
        userThree.setUsername("Minka updated from app :)");
        em.persist(userThree);


        System.out.println("=====================");


        // Get list of all entities again (users)
        Iterable<User> allUsersAgain = em.find(User.class);
        System.out.println("ALL USERS AGAIN:");
        for (User userResult : allUsersAgain) {
            System.out.printf("Id: %d, Username: %s | Password: %s%n",
                    userResult.getId(),
                    userResult.getUsername(),
                    userResult.getPass()
            );
        }


        // test joins
        // experimental
        for (User u : em.compose(User.class, null)
                .deepInnerJoin(Book.class)
                .innerJoin(Hero.class)
                .toEntity(User.class)
                .get()) {
            System.out.println(u.getUsername());
        }
    }
}
