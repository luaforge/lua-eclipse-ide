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
 *     Numerous optimizations to reduce the round trip "var" command requests
 *     Globals and tables have their values retrieved once in a serialized form
 *     Usually, getValue would make a var request for each variable. However,
 *       storing globals and tables reduces the round trips.
 *     In order to know that certain variables have already been retrieved there
 *       are booleans that table or global that indicate 1 or more values
 *       have already been retrieved.
******************************************************************************/
package org.eclipse.debug.examples.core.pda.model;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

/**
 * A variable in a PDA stack frame
 */
public class PDAVariable extends PDADebugElement implements IVariable {
	
	// name & stack frmae
	private String fName;
	private PDAStackFrame fFrame;
	
	/**
	 * Whether this variable is an element of a Lua table
	 * This saves on round trips to the interpreter by getting all the
	 * elements of a table in one "var" request.
	 */
	private boolean table = false;

	/**
	 * Whether this variable is Lua global variable
	 * Lua globals had to be gathered and displayed completely
	 * differently from locals. This is because locals are tied to
	 * a PDAStackFrame (using the "senddCommand("var 1 x" to retrieve)
	 * while globals have no association to a stack frame. When and
	 * PDAVariable/PDAValue is about to be displayed, rather then retrieve
	 * it like a local, we recognize it is a global and get all globals
	 * in 1 list.
	 * 
	 * This saves on round trips to the interpreter by getting all the
	 * elements of a table in one sendCommand"getglobal" request.
	 */
	private boolean global = false;
	
	private String globalVal = null;
	
	/**
	 * This is the value part of an expression like "x[1]=abc"
	 * Saving it for tables reduces round trips to the Lua interpreter
	 * and also make indexing associative arrays easier. Then displaying
	 * is simpler also and is done when a PDAVarible is recognized as
	 * being a table.
	 */
	private String val = null;
	
	/**
	 * This is used in the case of a global table to simplify data retrieval
	 * and make data serialization between ldb and the IDE easier.
	 * Both this.global and this.table are set to true for this case.
	 */
	PDAVariable[] pdavars = null;
	
	/**
	 * Constructs a variable contained in the given stack frame
	 * with the given name.
	 * 
	 * @param frame owning stack frame
	 * @param name variable name
	 */
	public PDAVariable(PDAStackFrame frame, String name) {
		super(frame.getPDADebugTarget());
		fFrame = frame;
		fName = name;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IVariable#getValue()
	 */
	public IValue getValue() throws DebugException {
		// For globals and tables, we don't need to make a var request 
		// because the parent already retrieved everything in a list 
		// (in init) and saved an array of PDAVariables 
		if (this.isGlobal() && this.isTable()) {
			return new PDAValue(this.getPDADebugTarget(), "global table", this.pdavars);
		} else if (this.isGlobal()) {
			return new PDAValue(this.getPDADebugTarget(), this.globalVal);
		} else if (this.isTable()) {
			return new PDAValue(this.getPDADebugTarget(), this.val);
		} else {
			String value = sendRequest("var " + getStackFrame().getIdentifier() + " " + getName());
			if (value.indexOf("|") > -1) {
				// this is a list of table values, make them into an array of
				// IValues (PDAValue
				return init(value);
			} else {
			    return new PDAValue(this.getPDADebugTarget(), value);
			}
		}
	}
	
	private PDAValue init(String data) {
		String[] strings = data.split("\\|");
		
		int numVars = strings.length;
		PDAVariable[] vars = new PDAVariable[numVars];
		for (int i = 0; i < numVars; i++) {
			// strings[i] is something like "x[1]=abc"
			String[] nameVal = strings[i].split("\\=");
			// Save the "x[1]" here
			PDAVariable pdaVar = new PDAVariable(getStackFrame(), nameVal[0]);
			// Save the "abc" here
			pdaVar.val = nameVal[1];
			pdaVar.setTable(true);
			vars[i] = pdaVar;
		}

		return new PDAValue(this.getPDADebugTarget(), "table", vars);
	}
	
	public boolean isTable() {
		return this.table;
	}
	
	public void setTable(boolean val) {
		this.table = val;
	}
	
	public boolean isGlobal() {
		return this.global;
	}
	
	public void setGlobal(boolean val) {
		this.global = val;
	}

	public void setGlobalVal(String val) {
		this.globalVal = val;
	}

	public void setPDAVaribleValue(String value) {
		this.val = value;
	}

	public void setGlobalTablePDAVars(PDAVariable[] var) {
		this.pdavars = var;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IVariable#getName()
	 */
	public String getName() throws DebugException {
		return fName;
	}
	public void setName(String name) {
		this.fName = name;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IVariable#getReferenceTypeName()
	 */
	public String getReferenceTypeName() throws DebugException {
		return "Thing";
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IVariable#hasValueChanged()
	 */
	public boolean hasValueChanged() throws DebugException {
		return false;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValueModification#setValue(java.lang.String)
	 */
	public void setValue(String expression) throws DebugException {
		sendRequest("setvar " + getStackFrame().getIdentifier() + " " + getName() + " " + expression);
		fireChangeEvent(DebugEvent.CONTENT);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValueModification#setValue(org.eclipse.debug.core.model.IValue)
	 */
	public void setValue(IValue value) throws DebugException {
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValueModification#supportsValueModification()
	 */
	public boolean supportsValueModification() {
		return true;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValueModification#verifyValue(java.lang.String)
	 */
	public boolean verifyValue(String expression) throws DebugException {
		return true;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IValueModification#verifyValue(org.eclipse.debug.core.model.IValue)
	 */
	public boolean verifyValue(IValue value) throws DebugException {
		return false;
	}
	
	/**
	 * Returns the stack frame owning this variable.
	 * 
	 * @return the stack frame owning this variable
	 */
	protected PDAStackFrame getStackFrame() {
		return fFrame;
	}

}
