package com.t13max.ioc.context;



public interface HierarchicalMessageSource extends MessageSource {

	void setParentMessageSource( MessageSource parent);

	 MessageSource getParentMessageSource();

}
