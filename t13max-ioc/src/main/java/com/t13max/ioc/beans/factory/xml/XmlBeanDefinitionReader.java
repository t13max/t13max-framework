package com.t13max.ioc.beans.factory.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;


import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.t13max.ioc.beans.BeanUtils;
import com.t13max.ioc.beans.factory.BeanDefinitionStoreException;
import com.t13max.ioc.beans.factory.parsing.EmptyReaderEventListener;
import com.t13max.ioc.beans.factory.parsing.FailFastProblemReporter;
import com.t13max.ioc.beans.factory.parsing.NullSourceExtractor;
import com.t13max.ioc.beans.factory.parsing.ProblemReporter;
import com.t13max.ioc.beans.factory.parsing.ReaderEventListener;
import com.t13max.ioc.beans.factory.parsing.SourceExtractor;
import com.t13max.ioc.beans.factory.support.AbstractBeanDefinitionReader;
import com.t13max.ioc.beans.factory.support.BeanDefinitionRegistry;
import com.t13max.ioc.core.NamedThreadLocal;
import com.t13max.ioc.core.io.DescriptiveResource;
import com.t13max.ioc.core.io.Resource;
import com.t13max.ioc.core.io.ResourceLoader;
import com.t13max.ioc.core.io.support.EncodedResource;
import com.t13max.ioc.util.Assert;
import com.t13max.ioc.util.xml.SimpleSaxErrorHandler;
import com.t13max.ioc.util.xml.XmlValidationModeDetector;

public class XmlBeanDefinitionReader extends AbstractBeanDefinitionReader {

	public static final int VALIDATION_NONE = XmlValidationModeDetector.VALIDATION_NONE;

	public static final int VALIDATION_AUTO = XmlValidationModeDetector.VALIDATION_AUTO;

	public static final int VALIDATION_DTD = XmlValidationModeDetector.VALIDATION_DTD;

	public static final int VALIDATION_XSD = XmlValidationModeDetector.VALIDATION_XSD;


	private static final Map<String, Integer> constants = Map.of(
			"VALIDATION_NONE", VALIDATION_NONE,
			"VALIDATION_AUTO", VALIDATION_AUTO,
			"VALIDATION_DTD", VALIDATION_DTD,
			"VALIDATION_XSD", VALIDATION_XSD
		);
	private int validationMode = VALIDATION_AUTO;
	private boolean namespaceAware = false;
	private Class<? extends BeanDefinitionDocumentReader> documentReaderClass =
			DefaultBeanDefinitionDocumentReader.class;
	private ProblemReporter problemReporter = new FailFastProblemReporter();
	private ReaderEventListener eventListener = new EmptyReaderEventListener();
	private SourceExtractor sourceExtractor = new NullSourceExtractor();
	private  NamespaceHandlerResolver namespaceHandlerResolver;
	private DocumentLoader documentLoader = new DefaultDocumentLoader();
	private  EntityResolver entityResolver;
	private ErrorHandler errorHandler = new SimpleSaxErrorHandler(logger);
	private final XmlValidationModeDetector validationModeDetector = new XmlValidationModeDetector();
	private final ThreadLocal<Set<EncodedResource>> resourcesCurrentlyBeingLoaded = NamedThreadLocal.withInitial(
			"XML bean definition resources currently being loaded", () -> new HashSet<>(4));


	public XmlBeanDefinitionReader(BeanDefinitionRegistry registry) {
		super(registry);
	}


	public void setValidating(boolean validating) {
		this.validationMode = (validating ? VALIDATION_AUTO : VALIDATION_NONE);
		this.namespaceAware = !validating;
	}

	public void setValidationModeName(String validationModeName) {
		Assert.hasText(validationModeName, "'validationModeName' must not be null or blank");
		Integer validationMode = constants.get(validationModeName);
		Assert.notNull(validationMode, "Only validation mode constants allowed");
		this.validationMode = validationMode;
	}

	public void setValidationMode(int validationMode) {
		Assert.isTrue(constants.containsValue(validationMode),
				"Only values of validation mode constants allowed");
		this.validationMode = validationMode;
	}

	public int getValidationMode() {
		return this.validationMode;
	}

	public void setNamespaceAware(boolean namespaceAware) {
		this.namespaceAware = namespaceAware;
	}

	public boolean isNamespaceAware() {
		return this.namespaceAware;
	}

	public void setProblemReporter( ProblemReporter problemReporter) {
		this.problemReporter = (problemReporter != null ? problemReporter : new FailFastProblemReporter());
	}

	public void setEventListener( ReaderEventListener eventListener) {
		this.eventListener = (eventListener != null ? eventListener : new EmptyReaderEventListener());
	}

	public void setSourceExtractor( SourceExtractor sourceExtractor) {
		this.sourceExtractor = (sourceExtractor != null ? sourceExtractor : new NullSourceExtractor());
	}

	public void setNamespaceHandlerResolver( NamespaceHandlerResolver namespaceHandlerResolver) {
		this.namespaceHandlerResolver = namespaceHandlerResolver;
	}

	public void setDocumentLoader( DocumentLoader documentLoader) {
		this.documentLoader = (documentLoader != null ? documentLoader : new DefaultDocumentLoader());
	}

	public void setEntityResolver( EntityResolver entityResolver) {
		this.entityResolver = entityResolver;
	}

	protected EntityResolver getEntityResolver() {
		if (this.entityResolver == null) {
			// Determine default EntityResolver to use.
			ResourceLoader resourceLoader = getResourceLoader();
			if (resourceLoader != null) {
				this.entityResolver = new ResourceEntityResolver(resourceLoader);
			}
			else {
				this.entityResolver = new DelegatingEntityResolver(getBeanClassLoader());
			}
		}
		return this.entityResolver;
	}

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void setDocumentReaderClass(Class<? extends BeanDefinitionDocumentReader> documentReaderClass) {
		this.documentReaderClass = documentReaderClass;
	}


	@Override
	public int loadBeanDefinitions(Resource resource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(new EncodedResource(resource));
	}

	public int loadBeanDefinitions(EncodedResource encodedResource) throws BeanDefinitionStoreException {
		Assert.notNull(encodedResource, "EncodedResource must not be null");
		if (logger.isTraceEnabled()) {
			logger.trace("Loading XML bean definitions from " + encodedResource);
		}
		Set<EncodedResource> currentResources = this.resourcesCurrentlyBeingLoaded.get();
		if (!currentResources.add(encodedResource)) {
			throw new BeanDefinitionStoreException(
					"Detected cyclic loading of " + encodedResource + " - check your import definitions!");
		}
		try (InputStream inputStream = encodedResource.getResource().getInputStream()) {
			InputSource inputSource = new InputSource(inputStream);
			if (encodedResource.getEncoding() != null) {
				inputSource.setEncoding(encodedResource.getEncoding());
			}
			return doLoadBeanDefinitions(inputSource, encodedResource.getResource());
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"IOException parsing XML document from " + encodedResource.getResource(), ex);
		}
		finally {
			currentResources.remove(encodedResource);
			if (currentResources.isEmpty()) {
				this.resourcesCurrentlyBeingLoaded.remove();
			}
		}
	}

	public int loadBeanDefinitions(InputSource inputSource) throws BeanDefinitionStoreException {
		return loadBeanDefinitions(inputSource, "resource loaded through SAX InputSource");
	}

	public int loadBeanDefinitions(InputSource inputSource,  String resourceDescription)
			throws BeanDefinitionStoreException {
		return doLoadBeanDefinitions(inputSource, new DescriptiveResource(resourceDescription));
	}


	protected int doLoadBeanDefinitions(InputSource inputSource, Resource resource)
			throws BeanDefinitionStoreException {
		try {
			Document doc = doLoadDocument(inputSource, resource);
			int count = registerBeanDefinitions(doc, resource);
			if (logger.isDebugEnabled()) {
				logger.debug("Loaded " + count + " bean definitions from " + resource);
			}
			return count;
		}
		catch (BeanDefinitionStoreException ex) {
			throw ex;
		}
		catch (SAXParseException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"Line " + ex.getLineNumber() + " in XML document from " + resource + " is invalid", ex);
		}
		catch (SAXException ex) {
			throw new XmlBeanDefinitionStoreException(resource.getDescription(),
					"XML document from " + resource + " is invalid", ex);
		}
		catch (ParserConfigurationException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Parser configuration exception parsing XML from " + resource, ex);
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"IOException parsing XML document from " + resource, ex);
		}
		catch (Throwable ex) {
			throw new BeanDefinitionStoreException(resource.getDescription(),
					"Unexpected exception parsing XML document from " + resource, ex);
		}
	}

	protected Document doLoadDocument(InputSource inputSource, Resource resource) throws Exception {
		return this.documentLoader.loadDocument(inputSource, getEntityResolver(), this.errorHandler,
				getValidationModeForResource(resource), isNamespaceAware());
	}

	protected int getValidationModeForResource(Resource resource) {
		int validationModeToUse = getValidationMode();
		if (validationModeToUse != VALIDATION_AUTO) {
			return validationModeToUse;
		}
		int detectedMode = detectValidationMode(resource);
		if (detectedMode != VALIDATION_AUTO) {
			return detectedMode;
		}
		// Hmm, we didn't get a clear indication... Let's assume XSD,
		// since apparently no DTD declaration has been found up until
		// detection stopped (before finding the document's root tag).
		return VALIDATION_XSD;
	}

	protected int detectValidationMode(Resource resource) {
		if (resource.isOpen()) {
			throw new BeanDefinitionStoreException(
					"Passed-in Resource [" + resource + "] contains an open stream: " +
					"cannot determine validation mode automatically. Either pass in a Resource " +
					"that is able to create fresh streams, or explicitly specify the validationMode " +
					"on your XmlBeanDefinitionReader instance.");
		}
		InputStream inputStream;
		try {
			inputStream = resource.getInputStream();
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(
					"Unable to determine validation mode for [" + resource + "]: cannot open InputStream. " +
					"Did you attempt to load directly from a SAX InputSource without specifying the " +
					"validationMode on your XmlBeanDefinitionReader instance?", ex);
		}
		try {
			return this.validationModeDetector.detectValidationMode(inputStream);
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException("Unable to determine validation mode for [" +
					resource + "]: an error occurred whilst reading from the InputStream.", ex);
		}
	}

	public int registerBeanDefinitions(Document doc, Resource resource) throws BeanDefinitionStoreException {
		BeanDefinitionDocumentReader documentReader = createBeanDefinitionDocumentReader();
		int countBefore = getRegistry().getBeanDefinitionCount();
		documentReader.registerBeanDefinitions(doc, createReaderContext(resource));
		return getRegistry().getBeanDefinitionCount() - countBefore;
	}

	protected BeanDefinitionDocumentReader createBeanDefinitionDocumentReader() {
		return BeanUtils.instantiateClass(this.documentReaderClass);
	}

	public XmlReaderContext createReaderContext(Resource resource) {
		return new XmlReaderContext(resource, this.problemReporter, this.eventListener,
				this.sourceExtractor, this, getNamespaceHandlerResolver());
	}

	public NamespaceHandlerResolver getNamespaceHandlerResolver() {
		if (this.namespaceHandlerResolver == null) {
			this.namespaceHandlerResolver = createDefaultNamespaceHandlerResolver();
		}
		return this.namespaceHandlerResolver;
	}

	protected NamespaceHandlerResolver createDefaultNamespaceHandlerResolver() {
		ResourceLoader resourceLoader = getResourceLoader();
		ClassLoader cl = (resourceLoader != null ? resourceLoader.getClassLoader() : getBeanClassLoader());
		return new DefaultNamespaceHandlerResolver(cl);
	}

}
