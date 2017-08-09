package emuos.os.shell;

import java.lang.annotation.*;

/**
 * @author Link
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {
    String[] name();
}
