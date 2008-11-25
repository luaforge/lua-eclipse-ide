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
 *     Retarget to Lua.
 *     Need Lua file extensions to work
 *     The original example had breakpoints with 0 based line numbers,
 *       changed to more natural 1 based, as all components are 1 based.
******************************************************************************/
package org.eclipse.debug.examples.ui.pda.breakpoints;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ISuspendResume;
import org.eclipse.debug.examples.core.pda.DebugCorePlugin;
import org.eclipse.debug.examples.core.pda.breakpoints.PDARunToLineBreakpoint;
import org.eclipse.debug.ui.actions.IRunToLineTarget;
import org.eclipse.debug.ui.actions.RunToLineHandler;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.texteditor.ITextEditor;


/**
 * Run to line target for the Java debugger
 */
public class PDARunToLineAdapter implements IRunToLineTarget {
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.actions.IRunToLineTarget#runToLine(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection, org.eclipse.debug.core.model.ISuspendResume)
	 */
	public void runToLine(IWorkbenchPart part, ISelection selection, ISuspendResume target) throws CoreException {
		IEditorPart editorPart = (IEditorPart)part;
		ITextEditor textEditor = (ITextEditor)editorPart;
		ITextSelection textSelection = (ITextSelection) selection;
		int lineNumber = textSelection.getStartLine() + 1;
		if (lineNumber > 0) {
			if (target instanceof IAdaptable) {
				IDebugTarget debugTarget = (IDebugTarget) ((IAdaptable)target).getAdapter(IDebugTarget.class);
				if (debugTarget != null) {
					IFile resource = (IFile) textEditor.getEditorInput().getAdapter(IResource.class);
					IBreakpoint breakpoint= new PDARunToLineBreakpoint(resource, lineNumber);
					RunToLineHandler handler = new RunToLineHandler(debugTarget, target, breakpoint);
					handler.run(new NullProgressMonitor());
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.actions.IRunToLineTarget#canRunToLine(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection, org.eclipse.debug.core.model.ISuspendResume)
	 */
	public boolean canRunToLine(IWorkbenchPart part, ISelection selection, ISuspendResume target) {
		return target instanceof IDebugElement &&
		 ((IDebugElement)target).getModelIdentifier().equals(DebugCorePlugin.ID_PDA_DEBUG_MODEL);
	}
}
