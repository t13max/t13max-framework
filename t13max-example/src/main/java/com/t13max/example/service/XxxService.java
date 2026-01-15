package com.t13max.example.service;

import com.t13max.ioc.beans.factory.annotation.Autowired;
import com.t13max.ioc.stereotype.Service;
import lombok.extern.log4j.Log4j2;

/**
 * @Author: t13max
 * @Since: 22:28 2026/1/15
 */
@Log4j2
@Service
public class XxxService {

    @Autowired
    private BbbService bbbService;

    public void xxx() {
        bbbService.bbb();
    }

}
