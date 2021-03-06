package com.lxa.mvcframework.annotation;


import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LXAController {

    String value() default "";
}
