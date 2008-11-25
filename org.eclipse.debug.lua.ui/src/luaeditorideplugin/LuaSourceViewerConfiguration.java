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

//import org.eclipse.debug.examples.ui.pda.editor.TextHover;
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.IUndoManager;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;

public class LuaSourceViewerConfiguration extends SourceViewerConfiguration {

	public LuaSourceViewerConfiguration() {
		super();
	}

    /* (non-Javadoc)
     * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getTextHover(org.eclipse.jface.text.source.ISourceViewer, java.lang.String)
     */
    public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
        return new TextHover();
    }

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getAnnotationHover(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new AnnotationHover();
	}
    
	@Override
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		return LuaPlugin.MY_PARTITIONING;
	}

	@Override
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		return new String[] { IDocument.DEFAULT_CONTENT_TYPE,
				LuaPartitionScanner.SINGLELINE_COMMENT,
				LuaPartitionScanner.STRING,
				LuaPartitionScanner.MULTILINE_COMMENT};
	}

	@Override
	public IUndoManager getUndoManager(ISourceViewer sourceViewer) {
		// provide 25 levels of undo
		return new TextViewerUndoManager(25);
	}

	/**
	 * The reconciler is used for syntax highlighting, dividing the document
	 * into colors and font styles.
	 * 
	 * Use the default reconciler
	 * 
	 * @Override
	 */
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		return null;
	}

	/**
	 * The document partitions define the content types within a document, while
	 * the presentation reconciler divided those individual content types into
	 * tokens for sets of characters that share the same color and font style.
	 * For example, while may represent a keyword token for Java content, but it
	 * won't be represented in the same way within a multiline comment
	 * content.The presentation of the document must be maintained while the
	 * user continues to modify it. This is done through the use of a damager
	 * and a repairer. A damager takes the description of how the document was
	 * modified and returns a description of regions of the document that must
	 * be updated. For example, if a user types a multiline comment start all
	 * the text in the document up to multiline commend end must be repaired.
	 * The repairer takes care of actually repairing the region of the document
	 * that the damager output by using the rules for dividing the region into
	 * tokens and the color and font information associated with the tokens.
	 * 
	 * * @Override
	 */
	public IPresentationReconciler getPresentationReconciler(
			ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		DefaultDamagerRepairer dr = new DefaultDamagerRepairer(
				new LuaCodeScanner(new LuaColorProvider()));
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setDamager(dr, LuaPartitionScanner.MULTILINE_COMMENT);
		reconciler.setRepairer(dr, LuaPartitionScanner.MULTILINE_COMMENT);
		reconciler.setDamager(dr, LuaPartitionScanner.SINGLELINE_COMMENT);
		reconciler.setRepairer(dr, LuaPartitionScanner.SINGLELINE_COMMENT);
		reconciler.setDamager(dr, LuaPartitionScanner.STRING);
		reconciler.setRepairer(dr, LuaPartitionScanner.STRING);
		reconciler.setDamager(dr, LuaPartitionScanner.MULTILINE_STRING);
		reconciler.setRepairer(dr, LuaPartitionScanner.MULTILINE_STRING);

		return reconciler;
	}

	/**
	 * Auto edits are automatic source indenting, adding quotes, etc. in Eclipse
	 * terminology.
	 * 
	 * @Override
	 */
	public IAutoEditStrategy[] getAutoEditStrategies(
		ISourceViewer sourceViewer, String contentType) {
		IAutoEditStrategy strategy = new DefaultIndentLineAutoEditStrategy();
		return new IAutoEditStrategy[] { strategy };
	}
}
