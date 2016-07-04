package bg.royal.entity;

import bg.royal.orm.criteria.Criteria;
import bg.royal.orm.persistence.Column;
import bg.royal.orm.persistence.Entity;
import bg.royal.orm.persistence.Id;
import bg.royal.orm.persistence.relation.Join;

import java.util.ArrayList;

/**
 * Created by RoYaL on 7/3/2016.
 */
@Entity(name = "users")
public class User {

    @Id
    private Long id;

    private String username;

    @Column(name = "password")
    private String pass;

    @Join(table = Book.class)
    private Iterable<Book> books = new ArrayList<>();

    @Join(table = Hero.class)
    private Iterable<Hero> heroes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public Iterable<Book> getBooks() {
        return books;
    }

    public void setBooks(Iterable<Book> books) {
        this.books = books;
    }

    public Iterable<Hero> getHeroes() {
        return heroes;
    }

    public void setHeroes(Iterable<Hero> heroes) {
        this.heroes = heroes;
    }
}
