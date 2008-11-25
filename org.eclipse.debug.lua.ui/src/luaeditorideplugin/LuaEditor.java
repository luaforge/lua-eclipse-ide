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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.editors.text.TextEditor;

public class LuaEditor extends TextEditor {

	public LuaEditor() {
		super();
		setSourceViewerConfiguration(new LuaSourceViewerConfiguration());
		setDocumentProvider(new LuaDocumentProvider());

		// added for making hover work
		setRulerContextMenuId("pda.editor.rulerMenu");
		setEditorContextMenuId("pda.editor.editorMenu");
	}

	public void doSave(IProgressMonitor monitor) {
		super.doSave(monitor);
		// captured a file save, handle anything else
		System.out.println("saving file: " + this.getEditorInput().getName());
	}

	@Override
	protected ISourceViewer createSourceViewer(Composite parent,
			IVerticalRuler ruler, int styles) {
		ISourceViewer viewer = new LuaSourceViewer(parent, ruler,
				getOverviewRuler(), isOverviewRulerVisible(), styles, this);

		// ensure decoration support has been created and configured.
		getSourceViewerDecorationSupport(viewer);

		return viewer;
	}

}
