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
 *     Display the globals at the bottom of the local Variables view
 *     Get all globals at once to avoid a lot of extra calls to retrieve
 *     Display the left hand side of the global with the index because it
 *       could be an associative array, e.g. x["abc"]
 *     Deserialize the data for tables and globals and global tables.
 *     Store tables and globals in an array of a PDAVariable. This is so that
 *       we can catch the call to getValue and display all once rather then 
 *       for each variable. See PDAValue.
******************************************************************************/
package org.eclipse.debug.examples.core.pda.model;

import java.util.Vector;

import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IVariable;

/**
 * PDA stack frame.
 */
public class PDAStackFrame extends PDADebugElement implements IStackFrame {
	
	private PDAThread fThread;
	private String fName;
	private int fPC;
	private String fFileName;
	private int fId;
	
	static private final String startGlobalTable = "startglobaltable=startglobaltable";
	static private final String endGlobalTable = "endglobaltable=endglobaltable";
	
	/**
	 * Constructs a stack frame in the given thread with the given
	 * frame data.
	 * 
	 * @param thread
	 * @param data frame data
	 * @param id stack frame id (0 is the bottom of the stack)
	 */
	public PDAStackFrame(PDAThread thread, String data, int id) {
		super(thread.getPDADebugTarget());
		fId = id;
		fThread = thread;
		init(data);
	}
	
	/**
	 * Initializes this frame based on its data
	 * 
	 * @param data
	 */
	private void init(String data) {
		String[] strings = data.split("\\|");
		String fileName = strings[0];
		fFileName = (new Path(fileName)).lastSegment();
		String pc = strings[1];
		fPC = Integer.parseInt(pc);
		fName = strings[2];
		int numVars = strings.length - 3;
		IVariable[] vars = new IVariable[numVars];
		for (int i = 0; i < numVars; i++) {
			vars[i] = new PDAVariable(this, strings[i + 3]);
		}
		
		// I am adding the global variables to the end of every stack frame's
		// variable display window
	    try {
			data = sendRequest("getglobals");
		} catch (DebugException e) {
			strings = new String[0];
		}

		strings = data.split("\\|");

		// The list is serialized data that may have tables in it. The
		// tables have markers for start and end that should not be counted
		// as part of the list.
		// Everything between a start and an end marker is counted as 1 variable.
		// This is because those variables are stored in an array in the current
		// variable.
		int numGlobalVars = 0;
		boolean doCount = true;
		for(int i = 0; i < strings.length; i++) {
			if (doCount) {
				numGlobalVars++;
			}
			
			if (strings[i].equals(startGlobalTable)) {
				doCount = false;
			} else if (strings[i].equals(endGlobalTable)) {
				doCount = true;
			}
		}
		
		// make 2 fake variables between the locals and globals as labels
		int fakeGlobals = 2;
		numGlobalVars += fakeGlobals;
		
		IVariable[] globalVars = new PDAVariable[numGlobalVars];
		// make the fake globals first
		PDAVariable fakeVar = null;
		fakeVar = new PDAVariable(this, ""); // empty name as a space
		fakeVar.setGlobalVal("");            // empty value as a space
		fakeVar.setGlobal(true);
		globalVars[0] = fakeVar;
		fakeVar = new PDAVariable(this, "GLOBAL");
		fakeVar.setGlobalVal("VARIABLES");
		fakeVar.setGlobal(true);
		globalVars[1] = fakeVar;
		
		int countOfStringVars = 0;
		PDAVariable pdaVar = null;
		for (int i = 2; i < numGlobalVars; i++) {
			// strings[i] is something like "x[1]=abc"
			String curLine = strings[countOfStringVars++];
			String[] nameVal = curLine.split("\\=");
			// Save the "x[1]" here
			pdaVar = new PDAVariable(this, nameVal[0]);
            if (curLine.equals(startGlobalTable)) {
            	// make new PDAVariables in a loop until endGlobalTable marker
            	//
            	// look ahead to the next entry to get the name, e.g. x[1]=0
            	// just use the "x"
            	String name = strings[countOfStringVars+1];
            	name = name.split("\\[")[0];
            	pdaVar.setName(name);
            	pdaVar.setTable(true); // both table and global are set
            	pdaVar.setGlobal(true);
            	Vector vec = new Vector();
            	while (true) {
            		curLine = strings[countOfStringVars++];
            		if (curLine.equals(endGlobalTable)) {
            			break;
            		}
            		nameVal = curLine.split("\\=");
            		PDAVariable pVar = new PDAVariable(this, nameVal[0]);
            		pVar.setPDAVaribleValue(nameVal[1]);
            		pVar.setTable(true);
            		vec.add(pVar);
            	}
            	PDAVariable[] array = new PDAVariable[vec.size()];
            	vec.copyInto(array);
            	// put this array in the last pdaVar
            	pdaVar.setGlobalTablePDAVars(array);
            	globalVars[i] = pdaVar;
            	continue;
            }
            
			// Save the "abc" here
			pdaVar.setGlobalVal(nameVal[1]);
			pdaVar.setGlobal(true);
			globalVars[i] = pdaVar;
		}
		
		int total = numVars + numGlobalVars;
		IVariable[] totalVars = new PDAVariable[total];
		for (int i = 0; i < total; i++) {
			if (i < numVars) {
				totalVars[i] = vars[i];
			} else {
				totalVars[i] = globalVars[i-numVars];
			}
		}
		vars = totalVars;

		fThread.setVariables(this, vars);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getThread()
	 */
	public IThread getThread() {
		return fThread;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getVariables()
	 */
	public IVariable[] getVariables() throws DebugException {
		return fThread.getVariables(this);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#hasVariables()
	 */
	public boolean hasVariables() throws DebugException {
		return getVariables().length > 0;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getLineNumber()
	 */
	public int getLineNumber() throws DebugException {
		return fPC;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getCharStart()
	 */
	public int getCharStart() throws DebugException {
		return -1;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getCharEnd()
	 */
	public int getCharEnd() throws DebugException {
		return -1;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getName()
	 */
	public String getName() throws DebugException {
		return fName;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#getRegisterGroups()
	 */
	public IRegisterGroup[] getRegisterGroups() throws DebugException {
		return null;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStackFrame#hasRegisterGroups()
	 */
	public boolean hasRegisterGroups() throws DebugException {
		return false;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#canStepInto()
	 */
	public boolean canStepInto() {
		return getThread().canStepInto();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#canStepOver()
	 */
	public boolean canStepOver() {
		return getThread().canStepOver();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#canStepReturn()
	 */
	public boolean canStepReturn() {
		return getThread().canStepReturn();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#isStepping()
	 */
	public boolean isStepping() {
		return getThread().isStepping();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#stepInto()
	 */
	public void stepInto() throws DebugException {
		getThread().stepInto();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#stepOver()
	 */
	public void stepOver() throws DebugException {
		getThread().stepOver();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.IStep#stepReturn()
	 */
	public void stepReturn() throws DebugException {
		getThread().stepReturn();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ISuspendResume#canResume()
	 */
	public boolean canResume() {
		return getThread().canResume();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ISuspendResume#canSuspend()
	 */
	public boolean canSuspend() {
		return getThread().canSuspend();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ISuspendResume#isSuspended()
	 */
	public boolean isSuspended() {
		return getThread().isSuspended();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ISuspendResume#resume()
	 */
	public void resume() throws DebugException {
		getThread().resume();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ISuspendResume#suspend()
	 */
	public void suspend() throws DebugException {
		getThread().suspend();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#canTerminate()
	 */
	public boolean canTerminate() {
		return getThread().canTerminate();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#isTerminated()
	 */
	public boolean isTerminated() {
		return getThread().isTerminated();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ITerminate#terminate()
	 */
	public void terminate() throws DebugException {
		getThread().terminate();
	}
	
	/**
	 * Returns the name of the source file this stack frame is associated
	 * with.
	 * 
	 * @return the name of the source file this stack frame is associated
	 * with
	 */
	public String getSourceName() {
		return fFileName;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj instanceof PDAStackFrame) {
			PDAStackFrame sf = (PDAStackFrame)obj;
			return sf.getThread().equals(getThread()) && 
				sf.getSourceName().equals(getSourceName()) &&
				sf.fId == fId;
		}
		return false;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getSourceName().hashCode() + fId;
	}
	
	/**
	 * Returns this stack frame's unique identifier within its thread
	 * 
	 * @return this stack frame's unique identifier within its thread
	 */
	protected int getIdentifier() {
		return fId;
	}
	
	
}
