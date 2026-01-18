package com.t13max.ioc.beans.factory.parsing;



import com.t13max.ioc.core.io.Resource;

public class NullSourceExtractor implements SourceExtractor {

	@Override
	public  Object extractSource(Object sourceCandidate,  Resource definitionResource) {
		return null;
	}

}
