package com.t13max.ioc.aop.framework;

/**
 * 
 * @Author: t13max
 * @Since: 22:25 2026/1/16
 */
public interface AdvisedSupportListener {
	void activated(AdvisedSupport advised);
	void adviceChanged(AdvisedSupport advised);

}
