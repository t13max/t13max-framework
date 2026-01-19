package com.t13max.ioc.beans.factory.parsing;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class FailFastProblemReporter implements ProblemReporter {

	private Logger logger = LogManager.getLogger(getClass());

	
	public void setLogger( Log logger) {
		this.logger = (logger != null ? logger : LogFactory.getLog(getClass()));
	}

	
	@Override
	public void fatal(Problem problem) {
		throw new BeanDefinitionParsingException(problem);
	}
	
	@Override
	public void error(Problem problem) {
		throw new BeanDefinitionParsingException(problem);
	}
	
	@Override
	public void warning(Problem problem) {
		logger.warn(problem, problem.getRootCause());
	}

}
