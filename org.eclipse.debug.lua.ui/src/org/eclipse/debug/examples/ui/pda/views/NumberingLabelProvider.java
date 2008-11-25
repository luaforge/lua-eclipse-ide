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
 *     This is used to display the Lua stack
 *******************************************************************************/

package org.eclipse.debug.examples.ui.pda.views;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.examples.core.pda.model.PDAStackValue;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

public class NumberingLabelProvider extends LabelProvider {
	public static int index = 0;
	public Image getImage(Object element) {
		return null;
	}

	public String getText(Object element) {
		String retVal = "unknown";
		if (element instanceof PDAStackValue) {
			PDAStackValue psv = (PDAStackValue)element;
			String ind = "[" + index-- + "]";
			try {
				// get the fValues to line up by checking if ind is length 3 or 4
				if (ind.length() > 3) {
					// this is like "[10]"
					retVal = ind + "  " + psv.getValueString();
				} else {
					// this is like "[1]"
					retVal = ind + "    " + psv.getValueString();
				}
			} catch (DebugException e) {
				return "exception in display of fValue";
			}
		}
		
		return retVal;
	}
}
