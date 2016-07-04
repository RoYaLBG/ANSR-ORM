package bg.royal.entity;

import bg.royal.orm.persistence.Column;
import bg.royal.orm.persistence.Entity;
import bg.royal.orm.persistence.Id;
import bg.royal.orm.persistence.relation.Join;

import java.util.ArrayList;

/**
 * Created by RoYaL on 7/4/2016.
 */
@Entity(name = "books")
public class Book {

    @Id
    private Long id;

    private String name;

    @Join(table = User.class)
    @Column(name = "user_id")
    private User user;

    @Join(table = Hero.class)
    private Iterable<Hero> heroes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Iterable<Hero> getHeroes() {
        return heroes;
    }

    public void setHeroes(Iterable<Hero> heroes) {
        this.heroes = heroes;
    }
}
