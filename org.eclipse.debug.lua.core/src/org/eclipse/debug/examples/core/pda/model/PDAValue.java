/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Bjorn Freeman-Benson - initial API and implementation
 *******************************************************************************/
/******************************************************************************
 * Changes:
 *     Copyright (c) 2008 VeriSign, Inc. All rights reserved.
 *     John Rodriguez
 *     Added an array of PDAVariables that is used to hold values of globals
 *       and global tables
******************************************************************************/
package org.eclipse.debug.examples.core.pda.model;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

/**
 * Value of a PDA variable.
 */
public class PDAValue extends PDADebugElement implements IValue {
	
	private String fValue;
	// globals or a global table are placed in this array
	IVariable[] vars = new IVariable[0];
	
	public PDAValue(PDADebugTarget target, String value) {
		super(target);
		fValue = value;
	}

	// added a new constructor that adds the vars for globals or global tables
	public PDAValue(PDADebugTarget target, String value, PDAVariable[] variables) {
		super(target);
		fValue = value;
		this.vars = variables;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getReferenceTypeName()
	 */
	public String getReferenceTypeName() throws DebugException {
		try {
			Integer.parseInt(fValue);
		} catch (NumberFormatException e) {
			return "text";
		}
		return "integer";
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getValueString()
	 */
	public String getValueString() throws DebugException {
		return fValue;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#isAllocated()
	 */
	public boolean isAllocated() throws DebugException {
		return true;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#getVariables()
	 */
	public IVariable[] getVariables() throws DebugException {
		return vars;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValue#hasVariables()
	 */
	public boolean hasVariables() throws DebugException {
		// return fValue.split("\\W+").length > 1;
		return vars.length > 0;
	}
	/*
	 *  (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
    public boolean equals(Object obj) {
        return obj instanceof PDAValue && ((PDAValue)obj).fValue.equals(fValue);
    }
    /*
     *  (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        return fValue.hashCode();
    }
}
