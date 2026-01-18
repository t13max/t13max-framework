package com.t13max.ioc.beans.factory.parsing;

import java.util.EventListener;

public interface ReaderEventListener extends EventListener {
	
	void defaultsRegistered(DefaultsDefinition defaultsDefinition);
	
	void componentRegistered(ComponentDefinition componentDefinition);
	
	void aliasRegistered(AliasDefinition aliasDefinition);
	
	void importProcessed(ImportDefinition importDefinition);

}
