package bg.royal.entity;

import bg.royal.orm.persistence.Column;
import bg.royal.orm.persistence.Entity;
import bg.royal.orm.persistence.Id;
import bg.royal.orm.persistence.relation.Join;

/**
 * Created by RoYaL on 7/4/2016.
 */
@Entity(name = "heroes")
public class Hero {

    @Id
    private Long id;

    private String name;

    @Join(table = User.class)
    @Column(name = "user_id")
    private User user;

    @Join(table = Book.class)
    @Column(name = "book_id")
    private Book book;


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
}
