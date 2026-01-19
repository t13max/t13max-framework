package com.t13max.ioc.beans;



public interface Mergeable {	
	boolean isMergeEnabled();	
	Object merge( Object parent);

}
