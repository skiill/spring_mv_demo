package com.lxa.mvcframework.annotation;


import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LXARequestParam {

    String value() default "";
}
