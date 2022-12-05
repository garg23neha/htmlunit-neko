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
package net.sourceforge.htmlunit.html.dom;

import org.w3c.dom.html.HTMLAppletElement;

/**
 * @version $Revision$ $Date$
 * @author <a href="mailto:arkin@exoffice.com">Assaf Arkin</a>
 * @see org.w3c.dom.html.HTMLAppletElement
 * @see HTMLElementImpl
 */
public class HTMLAppletElementImpl
    extends HTMLElementImpl
    implements HTMLAppletElement
{

    private static final long serialVersionUID = 8375794094117740967L;

    @Override
    public String getAlign()
    {
        return getAttribute( "align" );
    }


    @Override
    public void setAlign( String align )
    {
        setAttribute( "align", align );
    }


    @Override
    public String getAlt()
    {
        return getAttribute( "alt" );
    }


    @Override
    public void setAlt( String alt )
    {
        setAttribute( "alt", alt );
    }


    @Override
    public String getArchive()
    {
        return getAttribute( "archive" );
    }


    @Override
    public void setArchive( String archive )
    {
        setAttribute( "archive", archive );
    }


    @Override
    public String getCode()
    {
        return getAttribute( "code" );
    }


    @Override
    public void setCode( String code )
    {
        setAttribute( "code", code );
    }


    @Override
    public String getCodeBase()
    {
        return getAttribute( "codebase" );
    }


    @Override
    public void setCodeBase( String codeBase )
    {
        setAttribute( "codebase", codeBase );
    }


    @Override
    public String getHeight()
    {
        return getAttribute( "height" );
    }


    @Override
    public void setHeight( String height )
    {
        setAttribute( "height", height );
    }


    @Override
    public String getHspace()
    {
        return getAttribute( "height" );
    }


    @Override
    public void setHspace( String height )
    {
        setAttribute( "height", height );
    }


    @Override
    public String getName()
    {
        return getAttribute( "name" );
    }


    @Override
    public void setName( String name )
    {
        setAttribute( "name", name );
    }


    @Override
    public String getObject()
    {
        return getAttribute( "object" );
    }


    @Override
    public void setObject( String object )
    {
        setAttribute( "object", object );
    }


    @Override
    public String getVspace()
    {
        return getAttribute( "vspace" );
    }


    @Override
    public void setVspace( String vspace )
    {
        setAttribute( "vspace", vspace );
    }


    @Override
    public String getWidth()
    {
        return getAttribute( "width" );
    }


    @Override
    public void setWidth( String width )
    {
        setAttribute( "width", width );
    }


    /**
     * Constructor requires owner document.
     *
     * @param owner The owner HTML document
     */
    public HTMLAppletElementImpl( HTMLDocumentImpl owner, String name )
    {
        super( owner, name );
    }

}

