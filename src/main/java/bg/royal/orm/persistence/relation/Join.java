package bg.royal.orm.persistence.relation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by RoYaL on 7/4/2016.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Join {

    Class<? extends Object> table();

}
