package com.lxa.mvcframework.annotation;


import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LXAAutoWired {

    String value() default "";
}
