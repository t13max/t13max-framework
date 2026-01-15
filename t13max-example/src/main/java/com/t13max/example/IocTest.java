package com.t13max.example;

import com.t13max.example.service.XxxService;
import com.t13max.ioc.context.annotation.AnnotationConfigApplicationContext;

/**
 * 测试ioc
 *
 * @Author: t13max
 * @Since: 22:26 2026/1/15
 */
public class IocTest {

    public static void main(String[] args) throws Throwable {

        try (AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext("");) {

            XxxService xxxService = applicationContext.getBean(XxxService.class);

            xxxService.xxx();
        }

    }
}
