/*-
 * #%L
 * com.paremus.dosgi.net
 * %%
 * Copyright (C) 2016 - 2019 Paremus Ltd
 * %%
 * Licensed under the Fair Source License, Version 0.9 (the "License");
 * 
 * See the NOTICE.txt file distributed with this work for additional 
 * information regarding copyright ownership. You may not use this file 
 * except in compliance with the License. For usage restrictions see the 
 * LICENSE.txt file distributed with this work
 * #L%
 */
/*
 Copyright 2008-2011 the original author or authors

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.paremus.dosgi.dsw.net.serialization;

import java.io.Serializable;


public class PrimitivePojo implements Serializable {
    private static final long serialVersionUID = 1L;

    public final boolean booleanField;
    public final byte byteField;
    public final short shortField;
    public final char charField;
    public final int intField;
    public final float floatField;
    public final long longField;
    public final double doubleField;

    public PrimitivePojo(boolean booleanField,
                         byte byteField,
                         short shortField,
                         char charField,
                         int intField,
                         float floatField,
                         long longField,
                         double doubleField) {
        this.booleanField = booleanField;
        this.byteField = byteField;
        this.shortField = shortField;
        this.charField = charField;
        this.intField = intField;
        this.floatField = floatField;
        this.longField = longField;
        this.doubleField = doubleField;
    }

}
