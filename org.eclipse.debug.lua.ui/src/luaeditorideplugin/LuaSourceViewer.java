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

import org.eclipse.jface.text.source.IOverviewRuler;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.widgets.Composite;

/* class extended in case we need to do override something later, johnr*/
public class LuaSourceViewer extends SourceViewer {

	private LuaEditor luaEditor = null;
	
	public LuaSourceViewer(Composite parent, IVerticalRuler ruler, int styles, LuaEditor le) {
		super(parent, ruler, styles);
		luaEditor = le;
	}

	public LuaSourceViewer(Composite parent, IVerticalRuler verticalRuler, IOverviewRuler overviewRuler, boolean showAnnotationsOverview, int styles, LuaEditor le) {
		super(parent, verticalRuler, overviewRuler, showAnnotationsOverview, styles);
		luaEditor = le;
	}
}
