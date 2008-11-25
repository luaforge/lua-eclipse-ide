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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.ui.editors.text.FileDocumentProvider;
import org.eclipse.jface.text.rules.FastPartitioner;

public class LuaDocumentProvider extends FileDocumentProvider {

	@Override
	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);

		if (document instanceof IDocumentExtension3) {
			IDocumentExtension3 extension3 = (IDocumentExtension3) document;
			IDocumentPartitioner partitioner = new FastPartitioner(LuaPlugin
					.getMyPartitionScanner(),
					LuaPartitionScanner.PARTITION_TYPES);
			extension3.setDocumentPartitioner(LuaPlugin.MY_PARTITIONING,
					partitioner);
			partitioner.connect(document);
		}

		return document;
	}

}
