/*
 * Copyright 2017-2023 Ronald Brill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.htmlunit.cyberneko.xerces.util;

import java.io.PrintWriter;

import org.htmlunit.cyberneko.xerces.xni.XNIException;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLErrorHandler;
import org.htmlunit.cyberneko.xerces.xni.parser.XMLParseException;

/**
 * Default error handler.
 *
 * @author Andy Clark, IBM
 */
public class DefaultErrorHandler implements XMLErrorHandler {

    //
    // Data
    //

    /** Print writer. */
    private final PrintWriter fOut;

    /**
     * Constructs an error handler that prints error messages to
     * <code>System.err</code>.
     */
    public DefaultErrorHandler() {
        this(new PrintWriter(System.err));
    } // <init>()

    // Constructs an error handler that prints error messages to the
    // specified <code>PrintWriter</code>.
    public DefaultErrorHandler(PrintWriter out) {
        fOut = out;
    } // <init>(PrintWriter)

    //
    // ErrorHandler methods
    //

    /** Warning. */
    @Override
    public void warning(String domain, String key, XMLParseException ex) throws XNIException {
        printError("Warning", ex);
    } // warning(XMLParseException)

    /** Error. */
    @Override
    public void error(String domain, String key, XMLParseException ex) throws XNIException {
        printError("Error", ex);
    } // error(XMLParseException)

    /** Fatal error. */
    @Override
    public void fatalError(String domain, String key, XMLParseException ex) throws XNIException {
        printError("Fatal Error", ex);
        throw ex;
    } // fatalError(XMLParseException)

    //
    // Private methods
    //

    /** Prints the error message. */
    private void printError(String type, XMLParseException ex) {

        fOut.print("[");
        fOut.print(type);
        fOut.print("] ");
        String systemId = ex.getExpandedSystemId();
        if (systemId != null) {
            int index = systemId.lastIndexOf('/');
            if (index != -1)
                systemId = systemId.substring(index + 1);
            fOut.print(systemId);
        }
        fOut.print(':');
        fOut.print(ex.getLineNumber());
        fOut.print(':');
        fOut.print(ex.getColumnNumber());
        fOut.print(": ");
        fOut.print(ex.getMessage());
        fOut.println();
        fOut.flush();

    } // printError(String,SAXParseException)

} // class DefaultErrorHandler
