/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.htmlunit.xerces.parsers;

import java.io.CharConversionException;
import java.io.IOException;
import java.util.Locale;

import org.xml.sax.AttributeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.DocumentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.ext.Locator2;
import org.xml.sax.ext.Locator2Impl;

import net.sourceforge.htmlunit.xerces.impl.Constants;
import net.sourceforge.htmlunit.xerces.util.EntityResolver2Wrapper;
import net.sourceforge.htmlunit.xerces.util.EntityResolverWrapper;
import net.sourceforge.htmlunit.xerces.util.ErrorHandlerWrapper;
import net.sourceforge.htmlunit.xerces.util.SAXMessageFormatter;
import net.sourceforge.htmlunit.xerces.xni.Augmentations;
import net.sourceforge.htmlunit.xerces.xni.NamespaceContext;
import net.sourceforge.htmlunit.xerces.xni.QName;
import net.sourceforge.htmlunit.xerces.xni.XMLAttributes;
import net.sourceforge.htmlunit.xerces.xni.XMLLocator;
import net.sourceforge.htmlunit.xerces.xni.XMLResourceIdentifier;
import net.sourceforge.htmlunit.xerces.xni.XMLString;
import net.sourceforge.htmlunit.xerces.xni.XNIException;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLConfigurationException;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLEntityResolver;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLErrorHandler;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLInputSource;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLParseException;
import net.sourceforge.htmlunit.xerces.xni.parser.XMLParserConfiguration;

/**
 * This is the base class of all SAX parsers. It implements both the
 * SAX1 and SAX2 parser functionality, while the actual pipeline is
 * defined in the parser configuration.
 *
 * @author Arnaud Le Hors, IBM
 * @author Andy Clark, IBM
 */
public abstract class AbstractSAXParser
    extends AbstractXMLDocumentParser
    implements Parser, XMLReader // SAX1, SAX2
{

    //
    // Constants
    //

    // features

    /** Feature identifier: namespaces. */
    protected static final String NAMESPACES =
        Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE;

    /** Feature id: string interning. */
    protected static final String STRING_INTERNING =
        Constants.SAX_FEATURE_PREFIX + Constants.STRING_INTERNING_FEATURE;

    /** Feature identifier: allow notation and unparsed entity events to be sent out of order. */
    // this is not meant to be a recognized feature, but we need it here to use
    // if it is already a recognized feature for the pipeline
    protected static final String ALLOW_UE_AND_NOTATION_EVENTS =
        Constants.SAX_FEATURE_PREFIX + Constants.ALLOW_DTD_EVENTS_AFTER_ENDDTD_FEATURE;

    /** Recognized features. */
    private static final String[] RECOGNIZED_FEATURES = {
        NAMESPACES,
        STRING_INTERNING,
    };

    // properties

    /** Property id: lexical handler. */
    protected static final String LEXICAL_HANDLER =
        Constants.SAX_PROPERTY_PREFIX + Constants.LEXICAL_HANDLER_PROPERTY;

    /** Property id: DOM node. */
    protected static final String DOM_NODE =
        Constants.SAX_PROPERTY_PREFIX + Constants.DOM_NODE_PROPERTY;

    /** Recognized properties. */
    private static final String[] RECOGNIZED_PROPERTIES = {
        LEXICAL_HANDLER,
        DOM_NODE,
    };

    //
    // Data
    //

    // features

    /** Namespaces. */
    protected boolean fNamespaces;

    /** Namespace prefixes. */
    protected boolean fNamespacePrefixes = false;

    /** Lexical handler parameter entities. */
    protected boolean fLexicalHandlerParameterEntities = true;

    /** Standalone document declaration. */
    protected boolean fStandalone;

    /** Resolve DTD URIs. */
    protected boolean fResolveDTDURIs = true;

    /** Use EntityResolver2. */
    protected boolean fUseEntityResolver2 = true;

    /**
     * XMLNS URIs: Namespace declarations in the
     * http://www.w3.org/2000/xmlns/ namespace.
     */
    protected boolean fXMLNSURIs = false;

    // parser handlers

    /** Content handler. */
    protected ContentHandler fContentHandler;

    /** Document handler. */
    protected DocumentHandler fDocumentHandler;

    /** Namespace context */
    protected NamespaceContext fNamespaceContext;

    /** DTD handler. */
    protected org.xml.sax.DTDHandler fDTDHandler;

    /** Lexical handler. */
    protected LexicalHandler fLexicalHandler;

    protected final QName fQName = new QName();

    // state

    /**
     * True if a parse is in progress. This state is needed because
     * some features/properties cannot be set while parsing (e.g.
     * validation and namespaces).
     */
    protected final boolean fParseInProgress = false;

    // track the version of the document being parsed
    protected String fVersion;

    // temp vars
    private final AttributesProxy fAttributesProxy = new AttributesProxy();

    // Default constructor.
    protected AbstractSAXParser(XMLParserConfiguration config) {
        super(config);

        config.addRecognizedFeatures(RECOGNIZED_FEATURES);
        config.addRecognizedProperties(RECOGNIZED_PROPERTIES);

        try {
            config.setFeature(ALLOW_UE_AND_NOTATION_EVENTS, false);
        }
        catch (XMLConfigurationException e) {
            // it wasn't a recognized feature, so we don't worry about it
        }
    }

    /**
     * The start of the document.
     *
     * @param locator The document locator, or null if the document
     *                 location cannot be reported during the parsing
     *                 of this document. However, it is <em>strongly</em>
     *                 recommended that a locator be supplied that can
     *                 at least report the system identifier of the
     *                 document.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal entities or a document entity that is
     *                 parsed from a java.io.Reader).
     * @param namespaceContext
     *                 The namespace context in effect at the
     *                 start of this document.
     *                 This object represents the current context.
     *                 Implementors of this class are responsible
     *                 for copying the namespace bindings from the
     *                 the current context (and its parent contexts)
     *                 if that information is important.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void startDocument(XMLLocator locator, String encoding,
                              NamespaceContext namespaceContext, Augmentations augs)
        throws XNIException {

        fNamespaceContext = namespaceContext;

        try {
            // SAX1
            if (fDocumentHandler != null) {
                if (locator != null) {
                    fDocumentHandler.setDocumentLocator(new LocatorProxy(locator));
                }
                // The application may have set the DocumentHandler to null
                // within setDocumentLocator() so we need to check again.
                if (fDocumentHandler != null) {
                    fDocumentHandler.startDocument();
                }
            }

            // SAX2
            if (fContentHandler != null) {
                if (locator != null) {
                    fContentHandler.setDocumentLocator(new LocatorProxy(locator));
                }
                // The application may have set the ContentHandler to null
                // within setDocumentLocator() so we need to check again.
                if (fContentHandler != null) {
                    fContentHandler.startDocument();
                }
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // startDocument(locator,encoding,augs)

    /**
     * Notifies of the presence of an XMLDecl line in the document. If
     * present, this method will be called immediately following the
     * startDocument call.
     *
     * @param version    The XML version.
     * @param encoding   The IANA encoding name of the document, or null if
     *                   not specified.
     * @param standalone The standalone value, or null if not specified.
     * @param augs   Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void xmlDecl(String version, String encoding, String standalone, Augmentations augs)
        throws XNIException {
        // the version need only be set once; if
        // document's XML 1.0|1.1, that's how it'll stay
        fVersion = version;
        fStandalone = "yes".equals(standalone);
    } // xmlDecl(String,String,String)

    /**
     * Notifies of the presence of the DOCTYPE line in the document.
     *
     * @param rootElement The name of the root element.
     * @param publicId    The public identifier if an external DTD or null
     *                    if the external DTD is specified using SYSTEM.
     * @param systemId    The system identifier if an external DTD, null
     *                    otherwise.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void doctypeDecl(String rootElement,
                            String publicId, String systemId, Augmentations augs)
        throws XNIException {

        try {
            // SAX2 extension
            if (fLexicalHandler != null) {
                fLexicalHandler.startDTD(rootElement, publicId, systemId);
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }
    } // doctypeDecl(String,String,String)

        /**
     * This method notifies of the start of an entity. The DTD has the
     * pseudo-name of "[dtd]" parameter entity names start with '%'; and
     * general entity names are just the entity name.
     * <p>
     * <strong>Note:</strong> Since the document is an entity, the handler
     * will be notified of the start of the document entity by calling the
     * startEntity method with the entity name "[xml]" <em>before</em> calling
     * the startDocument method. When exposing entity boundaries through the
     * SAX API, the document entity is never reported, however.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name     The name of the entity.
     * @param identifier The resource identifier.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal parameter entities).
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void startGeneralEntity(String name, XMLResourceIdentifier identifier,
                                   String encoding, Augmentations augs)
        throws XNIException {

        try {
            // Only report startEntity if this entity was actually read.
            if (augs != null && Boolean.TRUE.equals(augs.getItem(Constants.ENTITY_SKIPPED))) {
                // report skipped entity to content handler
                if (fContentHandler != null) {
                    fContentHandler.skippedEntity(name);
                }
            }
            else {
                // SAX2 extension
                if (fLexicalHandler != null) {
                    fLexicalHandler.startEntity(name);
                }
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // startGeneralEntity(String,String,String,String,String)

    /**
     * This method notifies the end of an entity. The DTD has the pseudo-name
     * of "[dtd]" parameter entity names start with '%'; and general entity
     * names are just the entity name.
     * <p>
     * <strong>Note:</strong> Since the document is an entity, the handler
     * will be notified of the end of the document entity by calling the
     * endEntity method with the entity name "[xml]" <em>after</em> calling
     * the endDocument method. When exposing entity boundaries through the
     * SAX API, the document entity is never reported, however.
     * <p>
     * <strong>Note:</strong> This method is not called for entity references
     * appearing as part of attribute values.
     *
     * @param name The name of the entity.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void endGeneralEntity(String name, Augmentations augs) throws XNIException {

        try {
            // Only report endEntity if this entity was actually read.
            if (augs == null || !Boolean.TRUE.equals(augs.getItem(Constants.ENTITY_SKIPPED))) {
                // SAX2 extension
                if (fLexicalHandler != null) {
                    fLexicalHandler.endEntity(name);
                }
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // endEntity(String)

     /**
     * The start of an element. If the document specifies the start element
     * by using an empty tag, then the startElement method will immediately
     * be followed by the endElement method, with no intervening methods.
     *
     * @param element    The name of the element.
     * @param attributes The element attributes.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
        throws XNIException {

        try {
            // SAX1
            if (fDocumentHandler != null) {
                // REVISIT: should we support schema-normalized-value for SAX1 events
                //
                fAttributesProxy.setAttributes(attributes);
                fDocumentHandler.startElement(element.rawname, fAttributesProxy);
            }

            // SAX2
            if (fContentHandler != null) {

                if (fNamespaces) {
                    // send prefix mapping events
                    startNamespaceMapping();
                }

                String uri = element.uri != null ? element.uri : "";
                String localpart = fNamespaces ? element.localpart : "";
                fAttributesProxy.setAttributes(attributes);
                fContentHandler.startElement(uri, localpart, element.rawname,
                                             fAttributesProxy);
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // startElement(QName,XMLAttributes)

    /**
     * Character content.
     *
     * @param text The content.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void characters(XMLString text, Augmentations augs) throws XNIException {

        // if type is union (XML Schema) it is possible that we receive
        // character call with empty data
        if (text.length == 0) {
            return;
        }


        try {
            // SAX1
            if (fDocumentHandler != null) {
                // REVISIT: should we support schema-normalized-value for SAX1 events
                //
                fDocumentHandler.characters(text.ch, text.offset, text.length);
            }

            // SAX2
            if (fContentHandler != null) {
                fContentHandler.characters(text.ch, text.offset, text.length);
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // characters(XMLString)

    /**
     * Ignorable whitespace. For this method to be called, the document
     * source must have some way of determining that the text containing
     * only whitespace characters should be considered ignorable. For
     * example, the validator can determine if a length of whitespace
     * characters in the document are ignorable based on the element
     * content model.
     *
     * @param text The ignorable whitespace.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {

        try {
            // SAX1
            if (fDocumentHandler != null) {
                fDocumentHandler.ignorableWhitespace(text.ch, text.offset, text.length);
            }

            // SAX2
            if (fContentHandler != null) {
                fContentHandler.ignorableWhitespace(text.ch, text.offset, text.length);
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // ignorableWhitespace(XMLString)

    /**
     * The end of an element.
     *
     * @param element The name of the element.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void endElement(QName element, Augmentations augs) throws XNIException {


        try {
            // SAX1
            if (fDocumentHandler != null) {
                fDocumentHandler.endElement(element.rawname);
            }

            // SAX2
            if (fContentHandler != null) {
                String uri = element.uri != null ? element.uri : "";
                String localpart = fNamespaces ? element.localpart : "";
                fContentHandler.endElement(uri, localpart,
                                           element.rawname);
                if (fNamespaces) {
                    endNamespaceMapping();
                }
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // endElement(QName)

        /**
     * The start of a CDATA section.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void startCDATA(Augmentations augs) throws XNIException {

        try {
            // SAX2 extension
            if (fLexicalHandler != null) {
                fLexicalHandler.startCDATA();
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // startCDATA()

    /**
     * The end of a CDATA section.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void endCDATA(Augmentations augs) throws XNIException {

        try {
            // SAX2 extension
            if (fLexicalHandler != null) {
                fLexicalHandler.endCDATA();
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // endCDATA()

    /**
     * A comment.
     *
     * @param text The text in the comment.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by application to signal an error.
     */
    @Override
    public void comment(XMLString text, Augmentations augs) throws XNIException {

        try {
            // SAX2 extension
            if (fLexicalHandler != null) {
                fLexicalHandler.comment(text.ch, 0, text.length);
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // comment(XMLString)

    /**
     * A processing instruction. Processing instructions consist of a
     * target name and, optionally, text data. The data is only meaningful
     * to the application.
     * <p>
     * Typically, a processing instruction's data will contain a series
     * of pseudo-attributes. These pseudo-attributes follow the form of
     * element attributes but are <strong>not</strong> parsed or presented
     * to the application as anything other than text. The application is
     * responsible for parsing the data.
     *
     * @param target The target.
     * @param data   The data or null if none specified.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void processingInstruction(String target, XMLString data, Augmentations augs)
        throws XNIException {

        //
        // REVISIT - I keep running into SAX apps that expect
        //   null data to be an empty string, which is contrary
        //   to the comment for this method in the SAX API.
        //

        try {
            // SAX1
            if (fDocumentHandler != null) {
                fDocumentHandler.processingInstruction(target,
                                                       data.toString());
            }

            // SAX2
            if (fContentHandler != null) {
                fContentHandler.processingInstruction(target, data.toString());
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // processingInstruction(String,XMLString)


    /**
     * The end of the document.
     * @param augs     Additional information that may include infoset augmentations
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    @Override
    public void endDocument(Augmentations augs) throws XNIException {

        try {
            // SAX1
            if (fDocumentHandler != null) {
                fDocumentHandler.endDocument();
            }

            // SAX2
            if (fContentHandler != null) {
                fContentHandler.endDocument();
            }
        }
        catch (SAXException e) {
            throw new XNIException(e);
        }

    } // endDocument()

    //
    // Parser and XMLReader methods
    //

    /**
     * Parses the input source specified by the given system identifier.
     * <p>
     * This method is equivalent to the following:
     * <pre>
     *     parse(new InputSource(systemId));
     * </pre>
     *
     * @param systemId The system identifier (URI).
     *
     * @exception org.xml.sax.SAXException Throws exception on SAX error.
     * @exception java.io.IOException Throws exception on i/o error.
     */
    @Override
    public void parse(String systemId) throws SAXException, IOException {

        // parse document
        XMLInputSource source = new XMLInputSource(null, systemId, null);
        try {
            parse(source);
        }

        // wrap XNI exceptions as SAX exceptions
        catch (XMLParseException e) {
            Exception ex = e.getException();
            if (ex == null || ex instanceof CharConversionException) {
                // must be a parser exception; mine it for locator info
                // and throw a SAXParseException
                Locator2Impl locatorImpl = new Locator2Impl();
                // since XMLParseExceptions know nothing about encoding,
                // we cannot return anything meaningful in this context.
                // We *could* consult the LocatorProxy, but the
                // application can do this itself if it wishes to possibly
                // be mislead.
                locatorImpl.setXMLVersion(fVersion);
                locatorImpl.setPublicId(e.getPublicId());
                locatorImpl.setSystemId(e.getExpandedSystemId());
                locatorImpl.setLineNumber(e.getLineNumber());
                locatorImpl.setColumnNumber(e.getColumnNumber());
                throw (ex == null) ?
                        new SAXParseException(e.getMessage(), locatorImpl) :
                        new SAXParseException(e.getMessage(), locatorImpl, ex);
            }
            if (ex instanceof SAXException) {
                // why did we create an XMLParseException?
                throw (SAXException)ex;
            }
            if (ex instanceof IOException) {
                throw (IOException)ex;
            }
            throw new SAXException(ex);
        }
        catch (XNIException e) {
            Exception ex = e.getException();
            if (ex == null) {
                throw new SAXException(e.getMessage());
            }
            if (ex instanceof SAXException) {
                throw (SAXException)ex;
            }
            if (ex instanceof IOException) {
                throw (IOException)ex;
            }
            throw new SAXException(ex);
        }

    } // parse(String)

    /**
     * {@inheritDoc}
     */
    @Override
    public void parse(InputSource inputSource)
        throws SAXException, IOException {

        // parse document
        try {
            XMLInputSource xmlInputSource =
                new XMLInputSource(inputSource.getPublicId(),
                                   inputSource.getSystemId(),
                                   null);
            xmlInputSource.setByteStream(inputSource.getByteStream());
            xmlInputSource.setCharacterStream(inputSource.getCharacterStream());
            xmlInputSource.setEncoding(inputSource.getEncoding());
            parse(xmlInputSource);
        }

        // wrap XNI exceptions as SAX exceptions
        catch (XMLParseException e) {
            Exception ex = e.getException();
            if (ex == null || ex instanceof CharConversionException) {
                // must be a parser exception; mine it for locator info
                // and throw a SAXParseException
                Locator2Impl locatorImpl = new Locator2Impl();
                // since XMLParseExceptions know nothing about encoding,
                // we cannot return anything meaningful in this context.
                // We *could* consult the LocatorProxy, but the
                // application can do this itself if it wishes to possibly
                // be mislead.
                locatorImpl.setXMLVersion(fVersion);
                locatorImpl.setPublicId(e.getPublicId());
                locatorImpl.setSystemId(e.getExpandedSystemId());
                locatorImpl.setLineNumber(e.getLineNumber());
                locatorImpl.setColumnNumber(e.getColumnNumber());
                throw (ex == null) ?
                        new SAXParseException(e.getMessage(), locatorImpl) :
                        new SAXParseException(e.getMessage(), locatorImpl, ex);
            }
            if (ex instanceof SAXException) {
                // why did we create an XMLParseException?
                throw (SAXException)ex;
            }
            if (ex instanceof IOException) {
                throw (IOException)ex;
            }
            throw new SAXException(ex);
        }
        catch (XNIException e) {
            Exception ex = e.getException();
            if (ex == null) {
                throw new SAXException(e.getMessage());
            }
            if (ex instanceof SAXException) {
                throw (SAXException)ex;
            }
            if (ex instanceof IOException) {
                throw (IOException)ex;
            }
            throw new SAXException(ex);
        }

    } // parse(InputSource)

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEntityResolver(EntityResolver resolver) {

        try {
            XMLEntityResolver xer = (XMLEntityResolver) fConfiguration.getProperty(ENTITY_RESOLVER);
            if (fUseEntityResolver2 && resolver instanceof EntityResolver2) {
                if (xer instanceof EntityResolver2Wrapper) {
                    EntityResolver2Wrapper er2w = (EntityResolver2Wrapper) xer;
                    er2w.setEntityResolver((EntityResolver2) resolver);
                }
                else {
                    fConfiguration.setProperty(ENTITY_RESOLVER,
                            new EntityResolver2Wrapper((EntityResolver2) resolver));
                }
            }
            else {
                if (xer instanceof EntityResolverWrapper) {
                    EntityResolverWrapper erw = (EntityResolverWrapper) xer;
                    erw.setEntityResolver(resolver);
                }
                else {
                    fConfiguration.setProperty(ENTITY_RESOLVER,
                            new EntityResolverWrapper(resolver));
                }
            }
        }
        catch (XMLConfigurationException e) {
            // do nothing
        }

    } // setEntityResolver(EntityResolver)

    /**
     * Return the current entity resolver.
     *
     * @return The current entity resolver, or null if none
     *         has been registered.
     * @see #setEntityResolver
     */
    @Override
    public EntityResolver getEntityResolver() {

        EntityResolver entityResolver = null;
        try {
            XMLEntityResolver xmlEntityResolver =
                (XMLEntityResolver)fConfiguration.getProperty(ENTITY_RESOLVER);
            if (xmlEntityResolver != null) {
                if (xmlEntityResolver instanceof EntityResolverWrapper) {
                    entityResolver =
                        ((EntityResolverWrapper) xmlEntityResolver).getEntityResolver();
                }
                else if (xmlEntityResolver instanceof EntityResolver2Wrapper) {
                    entityResolver =
                        ((EntityResolver2Wrapper) xmlEntityResolver).getEntityResolver();
                }
            }
        }
        catch (XMLConfigurationException e) {
            // do nothing
        }
        return entityResolver;

    } // getEntityResolver():EntityResolver

    /**
     * Allow an application to register an error event handler.
     *
     * <p>If the application does not register an error handler, all
     * error events reported by the SAX parser will be silently
     * ignored; however, normal processing may not continue.  It is
     * highly recommended that all SAX applications implement an
     * error handler to avoid unexpected bugs.</p>
     *
     * <p>Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.</p>
     *
     * @param errorHandler The error handler.
     * @see #getErrorHandler
     */
    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {

        try {
            XMLErrorHandler xeh = (XMLErrorHandler) fConfiguration.getProperty(ERROR_HANDLER);
            if (xeh instanceof ErrorHandlerWrapper) {
                ErrorHandlerWrapper ehw = (ErrorHandlerWrapper) xeh;
                ehw.setErrorHandler(errorHandler);
            }
            else {
                fConfiguration.setProperty(ERROR_HANDLER,
                        new ErrorHandlerWrapper(errorHandler));
            }
        }
        catch (XMLConfigurationException e) {
            // do nothing
        }

    } // setErrorHandler(ErrorHandler)

    /**
     * Return the current error handler.
     *
     * @return The current error handler, or null if none
     *         has been registered.
     * @see #setErrorHandler
     */
    @Override
    public ErrorHandler getErrorHandler() {

        ErrorHandler errorHandler = null;
        try {
            XMLErrorHandler xmlErrorHandler =
                (XMLErrorHandler)fConfiguration.getProperty(ERROR_HANDLER);
            if (xmlErrorHandler != null &&
                xmlErrorHandler instanceof ErrorHandlerWrapper) {
                errorHandler = ((ErrorHandlerWrapper)xmlErrorHandler).getErrorHandler();
            }
        }
        catch (XMLConfigurationException e) {
            // do nothing
        }
        return errorHandler;

    } // getErrorHandler():ErrorHandler

    /**
     * Set the locale to use for messages.
     *
     * @param locale The locale object to use for localization of messages.
     *
     * @exception SAXException An exception thrown if the parser does not
     *                         support the specified locale.
     *
     * @see org.xml.sax.Parser
     */
    @Override
    public void setLocale(Locale locale) throws SAXException {
        //REVISIT:this methods is not part of SAX2 interfaces, we should throw exception
        //if any application uses SAX2 and sets locale also. -nb
        fConfiguration.setLocale(locale);

    } // setLocale(Locale)

    /**
     * Allow an application to register a DTD event handler.
     * <p>
     * If the application does not register a DTD handler, all DTD
     * events reported by the SAX parser will be silently ignored.
     * <p>
     * Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.
     *
     * @param dtdHandler The DTD handler.
     *

     * @see #getDTDHandler
     */
    @Override
    public void setDTDHandler(DTDHandler dtdHandler) {
        fDTDHandler = dtdHandler;
    } // setDTDHandler(DTDHandler)

    //
    // Parser methods
    //

    /**
     * Allow an application to register a document event handler.
     * <p>
     * If the application does not register a document handler, all
     * document events reported by the SAX parser will be silently
     * ignored (this is the default behaviour implemented by
     * HandlerBase).
     * <p>
     * Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.
     *
     * @param documentHandler The document handler.
     */
    @Override
    public void setDocumentHandler(DocumentHandler documentHandler) {
        fDocumentHandler = documentHandler;
    } // setDocumentHandler(DocumentHandler)

    //
    // XMLReader methods
    //

    /**
     * Allow an application to register a content event handler.
     * <p>
     * If the application does not register a content handler, all
     * content events reported by the SAX parser will be silently
     * ignored.
     * <p>
     * Applications may register a new or different handler in the
     * middle of a parse, and the SAX parser must begin using the new
     * handler immediately.
     *
     * @param contentHandler The content handler.
     *
     * @see #getContentHandler
     */
    @Override
    public void setContentHandler(ContentHandler contentHandler) {
        fContentHandler = contentHandler;
    } // setContentHandler(ContentHandler)

    /**
     * Return the current content handler.
     *
     * @return The current content handler, or null if none
     *         has been registered.
     *
     * @see #setContentHandler
     */
    @Override
    public ContentHandler getContentHandler() {
        return fContentHandler;
    } // getContentHandler():ContentHandler

    /**
     * Return the current DTD handler.
     *
     * @return The current DTD handler, or null if none
     *         has been registered.
     * @see #setDTDHandler
     */
    @Override
    public DTDHandler getDTDHandler() {
        return fDTDHandler;
    } // getDTDHandler():DTDHandler

    /**
     * Set the state of any feature in a SAX2 parser.  The parser
     * might not recognize the feature, and if it does recognize
     * it, it might not be able to fulfill the request.
     *
     * @param featureId The unique identifier (URI) of the feature.
     * @param state The requested state of the feature (true or false).
     *
     * @exception SAXNotRecognizedException If the
     *            requested feature is not known.
     * @exception SAXNotSupportedException If the
     *            requested feature is known, but the requested
     *            state is not supported.
     */
    @Override
    public void setFeature(String featureId, boolean state)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        try {
            //
            // SAX2 Features
            //

            if (featureId.startsWith(Constants.SAX_FEATURE_PREFIX)) {
                final int suffixLength = featureId.length() - Constants.SAX_FEATURE_PREFIX.length();

                // http://xml.org/sax/features/namespaces
                if (suffixLength == Constants.NAMESPACES_FEATURE.length() &&
                    featureId.endsWith(Constants.NAMESPACES_FEATURE)) {
                    fConfiguration.setFeature(featureId, state);
                    fNamespaces = state;
                    return;
                }

                // http://xml.org/sax/features/namespace-prefixes
                //   controls the reporting of raw prefixed names and Namespace
                //   declarations (xmlns* attributes): when this feature is false
                //   (the default), raw prefixed names may optionally be reported,
                //   and xmlns* attributes must not be reported.
                //
                if (suffixLength == Constants.NAMESPACE_PREFIXES_FEATURE.length() &&
                    featureId.endsWith(Constants.NAMESPACE_PREFIXES_FEATURE)) {
                    fNamespacePrefixes = state;
                    return;
                }

                // http://xml.org/sax/features/string-interning
                //   controls the use of java.lang.String#intern() for strings
                //   passed to SAX handlers.
                //
                if (suffixLength == Constants.STRING_INTERNING_FEATURE.length() &&
                    featureId.endsWith(Constants.STRING_INTERNING_FEATURE)) {
                    if (!state) {
                        throw new SAXNotSupportedException(
                            SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                            "false-not-supported", new Object [] {featureId}));
                    }
                    return;
                }

                // http://xml.org/sax/features/lexical-handler/parameter-entities
                //   controls whether the beginning and end of parameter entities
                //   will be reported to the LexicalHandler.
                //
                if (suffixLength == Constants.LEXICAL_HANDLER_PARAMETER_ENTITIES_FEATURE.length() &&
                    featureId.endsWith(Constants.LEXICAL_HANDLER_PARAMETER_ENTITIES_FEATURE)) {
                    fLexicalHandlerParameterEntities = state;
                    return;
                }

                // http://xml.org/sax/features/resolve-dtd-uris
                //   controls whether system identifiers will be absolutized relative to
                //   their base URIs before reporting.
                //
                if (suffixLength == Constants.RESOLVE_DTD_URIS_FEATURE.length() &&
                    featureId.endsWith(Constants.RESOLVE_DTD_URIS_FEATURE)) {
                    fResolveDTDURIs = state;
                    return;
                }

                // http://xml.org/sax/features/unicode-normalization-checking
                //   controls whether Unicode normalization checking is performed
                //   as per Appendix B of the XML 1.1 specification
                //
                if (suffixLength == Constants.UNICODE_NORMALIZATION_CHECKING_FEATURE.length() &&
                    featureId.endsWith(Constants.UNICODE_NORMALIZATION_CHECKING_FEATURE)) {
                    // REVISIT: Allow this feature to be set once Unicode normalization
                    // checking is supported -- mrglavas.
                    if (state) {
                        throw new SAXNotSupportedException(
                            SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                            "true-not-supported", new Object [] {featureId}));
                    }
                    return;
                }

                // http://xml.org/sax/features/xmlns-uris
                //   controls whether the parser reports that namespace declaration
                //   attributes as being in the namespace: http://www.w3.org/2000/xmlns/
                //
                if (suffixLength == Constants.XMLNS_URIS_FEATURE.length() &&
                    featureId.endsWith(Constants.XMLNS_URIS_FEATURE)) {
                    fXMLNSURIs = state;
                    return;
                }

                // http://xml.org/sax/features/use-entity-resolver2
                //   controls whether the methods of an object implementing
                //   org.xml.sax.ext.EntityResolver2 will be used by the parser.
                //
                if (suffixLength == Constants.USE_ENTITY_RESOLVER2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_ENTITY_RESOLVER2_FEATURE)) {
                    if (state != fUseEntityResolver2) {
                        fUseEntityResolver2 = state;
                        // Refresh EntityResolver wrapper.
                        setEntityResolver(getEntityResolver());
                    }
                    return;
                }

                //
                // Read only features.
                //

                // http://xml.org/sax/features/is-standalone
                //   reports whether the document specified a standalone document declaration.
                // http://xml.org/sax/features/use-attributes2
                //   reports whether Attributes objects passed to startElement also implement
                //   the org.xml.sax.ext.Attributes2 interface.
                // http://xml.org/sax/features/use-locator2
                //   reports whether Locator objects passed to setDocumentLocator also implement
                //   the org.xml.sax.ext.Locator2 interface.
                // http://xml.org/sax/features/xml-1.1
                //   reports whether the parser supports both XML 1.1 and XML 1.0.
                if ((suffixLength == Constants.IS_STANDALONE_FEATURE.length() &&
                    featureId.endsWith(Constants.IS_STANDALONE_FEATURE)) ||
                    (suffixLength == Constants.USE_ATTRIBUTES2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_ATTRIBUTES2_FEATURE)) ||
                    (suffixLength == Constants.USE_LOCATOR2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_LOCATOR2_FEATURE))) {
                    throw new SAXNotSupportedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                        "feature-read-only", new Object [] {featureId}));
                }


                //
                // Drop through and perform default processing
                //
            }

            //
            // Xerces Features
            //

            /*
            else if (featureId.startsWith(XERCES_FEATURES_PREFIX)) {
                String feature = featureId.substring(XERCES_FEATURES_PREFIX.length());
                //
                // Drop through and perform default processing
                //
            }
            */

            //
            // Default handling
            //

            fConfiguration.setFeature(featureId, state);
        }
        catch (XMLConfigurationException e) {
            String identifier = e.getIdentifier();
            if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                throw new SAXNotRecognizedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "feature-not-recognized", new Object [] {identifier}));
            }
            else {
                throw new SAXNotSupportedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "feature-not-supported", new Object [] {identifier}));
            }
        }

    } // setFeature(String,boolean)

    /**
     * Query the state of a feature.
     * <p>
     * Query the current state of any feature in a SAX2 parser.  The
     * parser might not recognize the feature.
     *
     * @param featureId The unique identifier (URI) of the feature
     *                  being set.
     * @return The current state of the feature.
     * @exception org.xml.sax.SAXNotRecognizedException If the
     *            requested feature is not known.
     * @exception SAXNotSupportedException If the
     *            requested feature is known but not supported.
     */
    @Override
    public boolean getFeature(String featureId)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        try {
            //
            // SAX2 Features
            //

            if (featureId.startsWith(Constants.SAX_FEATURE_PREFIX)) {
                final int suffixLength = featureId.length() - Constants.SAX_FEATURE_PREFIX.length();

                // http://xml.org/sax/features/namespace-prefixes
                //   controls the reporting of raw prefixed names and Namespace
                //   declarations (xmlns* attributes): when this feature is false
                //   (the default), raw prefixed names may optionally be reported,
                //   and xmlns* attributes must not be reported.
                //
                if (suffixLength == Constants.NAMESPACE_PREFIXES_FEATURE.length() &&
                    featureId.endsWith(Constants.NAMESPACE_PREFIXES_FEATURE)) {
                    return fNamespacePrefixes;
                }
                // http://xml.org/sax/features/string-interning
                //   controls the use of java.lang.String#intern() for strings
                //   passed to SAX handlers.
                //
                if (suffixLength == Constants.STRING_INTERNING_FEATURE.length() &&
                    featureId.endsWith(Constants.STRING_INTERNING_FEATURE)) {
                    return true;
                }

                // http://xml.org/sax/features/is-standalone
                //   reports whether the document specified a standalone document declaration.
                //
                if (suffixLength == Constants.IS_STANDALONE_FEATURE.length() &&
                    featureId.endsWith(Constants.IS_STANDALONE_FEATURE)) {
                    return fStandalone;
                }

                // http://xml.org/sax/features/lexical-handler/parameter-entities
                //   controls whether the beginning and end of parameter entities
                //   will be reported to the LexicalHandler.
                //
                if (suffixLength == Constants.LEXICAL_HANDLER_PARAMETER_ENTITIES_FEATURE.length() &&
                    featureId.endsWith(Constants.LEXICAL_HANDLER_PARAMETER_ENTITIES_FEATURE)) {
                    return fLexicalHandlerParameterEntities;
                }

                // http://xml.org/sax/features/resolve-dtd-uris
                //   controls whether system identifiers will be absolutized relative to
                //   their base URIs before reporting.
                if (suffixLength == Constants.RESOLVE_DTD_URIS_FEATURE.length() &&
                    featureId.endsWith(Constants.RESOLVE_DTD_URIS_FEATURE)) {
                    return fResolveDTDURIs;
                }

                // http://xml.org/sax/features/xmlns-uris
                //   controls whether the parser reports that namespace declaration
                //   attributes as being in the namespace: http://www.w3.org/2000/xmlns/
                //
                if (suffixLength == Constants.XMLNS_URIS_FEATURE.length() &&
                    featureId.endsWith(Constants.XMLNS_URIS_FEATURE)) {
                    return fXMLNSURIs;
                }

                // http://xml.org/sax/features/unicode-normalization-checking
                //   controls whether Unicode normalization checking is performed
                //   as per Appendix B of the XML 1.1 specification
                //
                if (suffixLength == Constants.UNICODE_NORMALIZATION_CHECKING_FEATURE.length() &&
                    featureId.endsWith(Constants.UNICODE_NORMALIZATION_CHECKING_FEATURE)) {
                    // REVISIT: Allow this feature to be set once Unicode normalization
                    // checking is supported -- mrglavas.
                    return false;
                }

                // http://xml.org/sax/features/use-entity-resolver2
                //   controls whether the methods of an object implementing
                //   org.xml.sax.ext.EntityResolver2 will be used by the parser.
                //
                if (suffixLength == Constants.USE_ENTITY_RESOLVER2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_ENTITY_RESOLVER2_FEATURE)) {
                    return fUseEntityResolver2;
                }

                // http://xml.org/sax/features/use-attributes2
                //   reports whether Attributes objects passed to startElement also implement
                //   the org.xml.sax.ext.Attributes2 interface.
                // http://xml.org/sax/features/use-locator2
                //   reports whether Locator objects passed to setDocumentLocator also implement
                //   the org.xml.sax.ext.Locator2 interface.
                //
                if ((suffixLength == Constants.USE_ATTRIBUTES2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_ATTRIBUTES2_FEATURE)) ||
                    (suffixLength == Constants.USE_LOCATOR2_FEATURE.length() &&
                    featureId.endsWith(Constants.USE_LOCATOR2_FEATURE))) {
                    return true;
                }


                //
                // Drop through and perform default processing
                //
            }

            //
            // Xerces Features
            //

            /*
            else if (featureId.startsWith(XERCES_FEATURES_PREFIX)) {
                //
                // Drop through and perform default processing
                //
            }
            */

            return fConfiguration.getFeature(featureId);
        }
        catch (XMLConfigurationException e) {
            String identifier = e.getIdentifier();
            if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                throw new SAXNotRecognizedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "feature-not-recognized", new Object [] {identifier}));
            }
            else {
                throw new SAXNotSupportedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "feature-not-supported", new Object [] {identifier}));
            }
        }

    } // getFeature(String):boolean

    /**
     * Set the value of any property in a SAX2 parser.  The parser
     * might not recognize the property, and if it does recognize
     * it, it might not support the requested value.
     *
     * @param propertyId The unique identifier (URI) of the property
     *                   being set.
     * @param value The value to which the property is being set.
     *
     * @exception SAXNotRecognizedException If the
     *            requested property is not known.
     * @exception SAXNotSupportedException If the
     *            requested property is known, but the requested
     *            value is not supported.
     */
    @Override
    public void setProperty(String propertyId, Object value)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        try {
            //
            // SAX2 core properties
            //

            if (propertyId.startsWith(Constants.SAX_PROPERTY_PREFIX)) {
                final int suffixLength = propertyId.length() - Constants.SAX_PROPERTY_PREFIX.length();

                //
                // http://xml.org/sax/properties/lexical-handler
                // Value type: org.xml.sax.ext.LexicalHandler
                // Access: read/write, pre-parse only
                //   Set the lexical event handler.
                //
                if (suffixLength == Constants.LEXICAL_HANDLER_PROPERTY.length() &&
                    propertyId.endsWith(Constants.LEXICAL_HANDLER_PROPERTY)) {
                    try {
                        setLexicalHandler((LexicalHandler)value);
                    }
                    catch (ClassCastException e) {
                        throw new SAXNotSupportedException(
                            SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                            "incompatible-class", new Object [] {propertyId, "org.xml.sax.ext.LexicalHandler"}));
                    }
                    return;
                }
                //
                // http://xml.org/sax/properties/dom-node
                // Value type: DOM Node
                // Access: read-only
                //   Get the DOM node currently being visited, if the SAX parser is
                //   iterating over a DOM tree.  If the parser recognises and
                //   supports this property but is not currently visiting a DOM
                //   node, it should return null (this is a good way to check for
                //   availability before the parse begins).
                // http://xml.org/sax/properties/document-xml-version
                // Value type: java.lang.String
                // Access: read-only
                //   The literal string describing the actual XML version of the document.
                //
                if ((suffixLength == Constants.DOM_NODE_PROPERTY.length() &&
                    propertyId.endsWith(Constants.DOM_NODE_PROPERTY)) ||
                    (suffixLength == Constants.DOCUMENT_XML_VERSION_PROPERTY.length() &&
                    propertyId.endsWith(Constants.DOCUMENT_XML_VERSION_PROPERTY))) {
                    throw new SAXNotSupportedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                        "property-read-only", new Object [] {propertyId}));
                }
                //
                // Drop through and perform default processing
                //
            }

            //
            // Xerces Properties
            //

            /*
            else if (propertyId.startsWith(XERCES_PROPERTIES_PREFIX)) {
                //
                // Drop through and perform default processing
                //
            }
            */

            //
            // Perform default processing
            //

            fConfiguration.setProperty(propertyId, value);
        }
        catch (XMLConfigurationException e) {
            String identifier = e.getIdentifier();
            if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                throw new SAXNotRecognizedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "property-not-recognized", new Object [] {identifier}));
            }
            else {
                throw new SAXNotSupportedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "property-not-supported", new Object [] {identifier}));
            }
        }

    } // setProperty(String,Object)

    /**
     * Query the value of a property.
     * <p>
     * Return the current value of a property in a SAX2 parser.
     * The parser might not recognize the property.
     *
     * @param propertyId The unique identifier (URI) of the property
     *                   being set.
     * @return The current value of the property.
     * @exception org.xml.sax.SAXNotRecognizedException If the
     *            requested property is not known.
     * @exception SAXNotSupportedException If the
     *            requested property is known but not supported.
     */
    @Override
    public Object getProperty(String propertyId)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        try {
            //
            // SAX2 core properties
            //

            if (propertyId.startsWith(Constants.SAX_PROPERTY_PREFIX)) {
                final int suffixLength = propertyId.length() - Constants.SAX_PROPERTY_PREFIX.length();

                //
                // http://xml.org/sax/properties/document-xml-version
                // Value type: java.lang.String
                // Access: read-only
                //   The literal string describing the actual XML version of the document.
                //
                if (suffixLength == Constants.DOCUMENT_XML_VERSION_PROPERTY.length() &&
                    propertyId.endsWith(Constants.DOCUMENT_XML_VERSION_PROPERTY)) {
                    return fVersion;
                }

                //
                // http://xml.org/sax/properties/lexical-handler
                // Value type: org.xml.sax.ext.LexicalHandler
                // Access: read/write, pre-parse only
                //   Set the lexical event handler.
                //
                if (suffixLength == Constants.LEXICAL_HANDLER_PROPERTY.length() &&
                    propertyId.endsWith(Constants.LEXICAL_HANDLER_PROPERTY)) {
                    return getLexicalHandler();
                }
                //
                // http://xml.org/sax/properties/dom-node
                // Value type: DOM Node
                // Access: read-only
                //   Get the DOM node currently being visited, if the SAX parser is
                //   iterating over a DOM tree.  If the parser recognises and
                //   supports this property but is not currently visiting a DOM
                //   node, it should return null (this is a good way to check for
                //   availability before the parse begins).
                //
                if (suffixLength == Constants.DOM_NODE_PROPERTY.length() &&
                    propertyId.endsWith(Constants.DOM_NODE_PROPERTY)) {
                    // we are not iterating a DOM tree
                    throw new SAXNotSupportedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                        "dom-node-read-not-supported", null));
                }

                //
                // Drop through and perform default processing
                //
            }

            //
            // Xerces properties
            //

            /*
            else if (propertyId.startsWith(XERCES_PROPERTIES_PREFIX)) {
                //
                // Drop through and perform default processing
                //
            }
            */

            //
            // Perform default processing
            //

            return fConfiguration.getProperty(propertyId);
        }
        catch (XMLConfigurationException e) {
            String identifier = e.getIdentifier();
            if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                throw new SAXNotRecognizedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "property-not-recognized", new Object [] {identifier}));
            }
            else {
                throw new SAXNotSupportedException(
                    SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                    "property-not-supported", new Object [] {identifier}));
            }
        }

    } // getProperty(String):Object

    //
    // Protected methods
    //

    // SAX2 core properties

    /**
     * Set the lexical event handler.
     * <p>
     * This method is the equivalent to the property:
     * <pre>
     * http://xml.org/sax/properties/lexical-handler
     * </pre>
     *
     * @param handler lexical event handler
     * @throws SAXNotRecognizedException on error
     * @throws SAXNotSupportedException on error
     *
     * @see #getLexicalHandler()
     * @see #setProperty(String, Object)
     */
    protected void setLexicalHandler(LexicalHandler handler)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        if (fParseInProgress) {
            throw new SAXNotSupportedException(
                SAXMessageFormatter.formatMessage(fConfiguration.getLocale(),
                "property-not-parsing-supported",
                new Object [] {"http://xml.org/sax/properties/lexical-handler"}));
        }
        fLexicalHandler = handler;

    }

    /**
     * @return the lexical handler.
     *
     * @throws SAXNotRecognizedException on error
     * @throws SAXNotSupportedException on error
     * @see #setLexicalHandler(LexicalHandler)
     */
    protected LexicalHandler getLexicalHandler()
        throws SAXNotRecognizedException, SAXNotSupportedException {
        return fLexicalHandler;
    }

    /**
     * Send startPrefixMapping events
     * @throws SAXException on error
     */
    protected final void startNamespaceMapping() throws SAXException{
        int count = fNamespaceContext.getDeclaredPrefixCount();
        if (count > 0) {
            String prefix;
            String uri;
            for (int i = 0; i < count; i++) {
                prefix = fNamespaceContext.getDeclaredPrefixAt(i);
                uri = fNamespaceContext.getURI(prefix);
                fContentHandler.startPrefixMapping(prefix,
                    (uri == null) ? "" : uri);
            }
        }
    }

    /**
     * Send endPrefixMapping events
     * @throws SAXException on error
     */
    protected final void endNamespaceMapping() throws SAXException {
        int count = fNamespaceContext.getDeclaredPrefixCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                fContentHandler.endPrefixMapping(fNamespaceContext.getDeclaredPrefixAt(i));
            }
        }
    }

    /**
     * Reset all components before parsing.
     *
     * @throws XNIException Thrown if an error occurs during initialization.
     */
    @Override
    public void reset() throws XNIException {
        super.reset();

        // reset state
        fVersion = "1.0";
        fStandalone = false;

        // features
        fNamespaces = fConfiguration.getFeature(NAMESPACES);

    } // reset()

    //
    // Classes
    //

    protected static final class LocatorProxy
        implements Locator2 {

        /** XML locator. */
        protected final XMLLocator fLocator;

        // Constructs an XML locator proxy.
        public LocatorProxy(XMLLocator locator) {
            fLocator = locator;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPublicId() {
            return fLocator.getPublicId();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getSystemId() {
            return fLocator.getExpandedSystemId();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getLineNumber() {
            return fLocator.getLineNumber();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getColumnNumber() {
            return fLocator.getColumnNumber();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getXMLVersion() {
            return fLocator.getXMLVersion();
        }

        /**
         * {@inheritDoc}
         */
        public String getEncoding() {
            return fLocator.getEncoding();
        }

    }

    protected static final class AttributesProxy
        implements AttributeList, Attributes2 {

        /** XML attributes. */
        protected XMLAttributes fAttributes;

        // Sets the XML attributes.
        public void setAttributes(XMLAttributes attributes) {
            fAttributes = attributes;
        } // setAttributes(XMLAttributes)

        @Override
        public int getLength() {
            return fAttributes.getLength();
        }

        @Override
        public String getName(int i) {
            return fAttributes.getQName(i);
        }

        @Override
        public String getQName(int index) {
            return fAttributes.getQName(index);
        }

        @Override
        public String getURI(int index) {
            // REVISIT: this hides the fact that internally we use
            //          null instead of empty string
            //          SAX requires URI to be a string or an empty string
            String uri= fAttributes.getURI(index);
            return uri != null ? uri : "";
        }

        @Override
        public String getLocalName(int index) {
            return fAttributes.getLocalName(index);
        }

        @Override
        public String getType(int i) {
            return fAttributes.getType(i);
        }

        @Override
        public String getType(String name) {
            return fAttributes.getType(name);
        }

        @Override
        public String getType(String uri, String localName) {
            return uri.length() == 0 ? fAttributes.getType(null, localName) :
                                       fAttributes.getType(uri, localName);
        }

        @Override
        public String getValue(int i) {
            return fAttributes.getValue(i);
        }

        @Override
        public String getValue(String name) {
            return fAttributes.getValue(name);
        }

        @Override
        public String getValue(String uri, String localName) {
            return uri.length() == 0 ? fAttributes.getValue(null, localName) :
                                       fAttributes.getValue(uri, localName);
        }

        @Override
        public int getIndex(String qName) {
            return fAttributes.getIndex(qName);
        }

        @Override
        public int getIndex(String uri, String localPart) {
            return uri.length() == 0 ? fAttributes.getIndex(null, localPart) :
                                       fAttributes.getIndex(uri, localPart);
        }

        // Attributes2 methods
        // REVISIT: Localize exception messages. -- mrglavas
        @Override
        public boolean isDeclared(int index) {
            if (index < 0 || index >= fAttributes.getLength()) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            return Boolean.TRUE.equals(
                fAttributes.getAugmentations(index).getItem(
                Constants.ATTRIBUTE_DECLARED));
        }

        @Override
        public boolean isDeclared(String qName) {
            int index = getIndex(qName);
            if (index == -1) {
                throw new IllegalArgumentException(qName);
            }
            return Boolean.TRUE.equals(
                fAttributes.getAugmentations(index).getItem(
                Constants.ATTRIBUTE_DECLARED));
        }

        @Override
        public boolean isDeclared(String uri, String localName) {
            int index = getIndex(uri, localName);
            if (index == -1) {
                throw new IllegalArgumentException(localName);
            }
            return Boolean.TRUE.equals(
                fAttributes.getAugmentations(index).getItem(
                Constants.ATTRIBUTE_DECLARED));
        }

        @Override
        public boolean isSpecified(int index) {
            if (index < 0 || index >= fAttributes.getLength()) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            return fAttributes.isSpecified(index);
        }

        @Override
        public boolean isSpecified(String qName) {
            int index = getIndex(qName);
            if (index == -1) {
                throw new IllegalArgumentException(qName);
            }
            return fAttributes.isSpecified(index);
        }

        @Override
        public boolean isSpecified(String uri, String localName) {
            int index = getIndex(uri, localName);
            if (index == -1) {
                throw new IllegalArgumentException(localName);
            }
            return fAttributes.isSpecified(index);
        }

    } // class AttributesProxy
} // class AbstractSAXParser
