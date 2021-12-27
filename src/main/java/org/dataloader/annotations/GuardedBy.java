package org.dataloader.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated element should be used only while holding the specified lock.
 */
@Target({FIELD, METHOD})
@Retention(CLASS)
public @interface GuardedBy {

  /**
   * The lock that should be held.
   */
  String value();
}
