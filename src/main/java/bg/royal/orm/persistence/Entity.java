package bg.royal.orm.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author Ivan Yonkov
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {

    String name();
}
