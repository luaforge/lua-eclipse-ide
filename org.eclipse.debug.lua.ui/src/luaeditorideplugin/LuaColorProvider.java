/*******************************************************************************
 * Copyright (c) 2008 VeriSign, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VeriSign, Inc. - initial API and implementation
 *     John Rodriguez
 *******************************************************************************/
package luaeditorideplugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * This class works with the LuaCodeScanner to color the
 * various Lua syntax elements.
 * 
 * @author jrodriguez
 *
 */
public class LuaColorProvider {

    public static final RGB MULTI_LINE_COMMENT= new RGB(128, 0, 0);
    public static final RGB SINGLE_LINE_COMMENT= new RGB(128, 128, 0);
    public static final RGB KEYWORD= new RGB(0, 0, 128);
    public static final RGB TYPE= new RGB(0, 0, 128);
    public static final RGB STRING= new RGB(0, 128, 0);
    public static final RGB MULTI_LINE_STRING= new RGB(0, 128, 128);
    public static final RGB DEFAULT= new RGB(128, 40, 0);


    protected Map fColorTable= new HashMap(10);

    public LuaColorProvider() {
    	
    }
    
    public void dispose() {
        Iterator e= fColorTable.values().iterator();
        while (e.hasNext())
            ((Color) e.next()).dispose();
    }


    public Color getColor(RGB rgb) {
        Color color= (Color) fColorTable.get(rgb);
        if (color == null) {
            color= new Color(Display.getCurrent(), rgb);
            fColorTable.put(rgb, color);
        }
        return color;
    }
}