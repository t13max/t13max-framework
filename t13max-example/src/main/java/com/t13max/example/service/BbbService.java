package com.t13max.example.service;

import com.t13max.ioc.stereotype.Component;
import lombok.extern.log4j.Log4j2;

/**
 * @Author: t13max
 * @Since: 22:29 2026/1/15
 */
@Log4j2
@Component
public class BbbService {

    private final XxxService xxxService;

    public BbbService(XxxService xxxService) {
        this.xxxService = xxxService;
    }

    public void bbb() {
        log.info("bbbbbbbb");
    }
}
