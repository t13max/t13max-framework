package com.t13max.ioc.beans.factory.parsing;



import com.t13max.ioc.core.io.Resource;

@FunctionalInterface
public interface SourceExtractor {
	
	 Object extractSource(Object sourceCandidate,  Resource definingResource);

}
