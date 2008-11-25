/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
// See the following article about writing editors:
// https://www6.software.ibm.com/developerworks/education/os-ecl-commplgin2/os-ecl-commplgin2-a4.pdf
package luaeditorideplugin;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

public class LuaAutoEditStrategy implements IAutoEditStrategy {
	public void customizeDocumentCommand(IDocument document,
			DocumentCommand command) {
		if (command.text.equals("\"")) {
			command.text = "\"\"";
			configureCommand(command);
		} else if (command.text.equals("'")) {
			command.text = "''";
			configureCommand(command);
		} else if (command.text.equals("{")) {
			int line;
			try {
				line = document.getLineOfOffset(command.offset);
			} catch (BadLocationException e1) {
				line = -1;
			}
			if (line > -1) {
				String indent;
				try {
					indent = getIndentOfLine(document, line);
				} catch (BadLocationException e) {
					indent = "";
				}
				command.text = "{" + "\r\n" + indent + "}";
				configureCommand(command);
			}
		}
	}

	private void configureCommand(DocumentCommand command) {
		// puts the caret between both the quotes

		command.caretOffset = command.offset + 1;
		command.shiftsCaret = false;
	}

	public static int findEndOfWhiteSpace(IDocument document, int offset,
			int end) throws BadLocationException {
		while (offset < end) {
			char c = document.getChar(offset);
			if (c != ' ' & c != '\t') {
				return offset;
			}
			offset++;
		}
		return end;
	}

	public static String getIndentOfLine(IDocument document, int line)
			throws BadLocationException {
		if (line > -1) {
			int start = document.getLineOffset(line);
			int end = start + document.getLineLength(line) - 1;
			int whiteend = findEndOfWhiteSpace(document, start, end);
			return document.get(start, whiteend - start);
		} else {
			return "";
		}
	}
}