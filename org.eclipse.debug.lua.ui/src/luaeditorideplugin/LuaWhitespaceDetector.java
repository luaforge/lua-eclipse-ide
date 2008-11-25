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

import org.eclipse.jface.text.rules.IWhitespaceDetector;

/**
 * A Lua aware white space detector.
 */
public class LuaWhitespaceDetector implements IWhitespaceDetector {
    /**
     * @see IWhitespaceDetector#isWhitespace
     */
    public boolean isWhitespace(char c) {
    	return (c == ' ' || c == '\t' || c == '\n' || c == '\r');
    }
}