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

import org.eclipse.jface.text.rules.IWordDetector;
/**
 * Java and Lua identifiers have the same rules (for now, johnr)
 */
public class LuaWordDetector implements IWordDetector {
    /*
     * @see IWordDetector#isWordStart
     */
    public boolean isWordStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }
    /*
     * @see IWordDetector#isWordPart
     */
    public boolean isWordPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }
}