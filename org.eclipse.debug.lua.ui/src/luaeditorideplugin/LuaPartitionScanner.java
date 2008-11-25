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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

/**
 * This class builds a scanner that works by simple patterns
 * The document is partitioned into simple content types like
 * strings, comments, etc.
 * 
 * This is used by the LuaDocumentProvider class to break the
 * content types into sections of the document.
 * 
 * Also see LuaSourceViewer class
 * 
 * 
 * @author jrodriguez
 *
 */
public class LuaPartitionScanner extends RuleBasedPartitionScanner {

	// string constants for different partition types
	public final static String SINGLELINE_COMMENT = "singleline_comment";
	public final static String MULTILINE_COMMENT= "multiline_comment";
	public final static String STRING = "string";
	public final static String MULTILINE_STRING = "multiline_string";
	public final static String[] PARTITION_TYPES = new String[] {
		MULTILINE_COMMENT, SINGLELINE_COMMENT, STRING, MULTILINE_STRING };

	/**
	 * Creates the partitioner and sets up the appropriate rules.
	 */
	public LuaPartitionScanner() {
		super();

		IToken singlelinecomment = new Token(SINGLELINE_COMMENT);
		IToken multilinecomment = new Token(MULTILINE_COMMENT);
		IToken string = new Token(STRING);
		IToken multilinestring = new Token(MULTILINE_STRING);

		List rules = new ArrayList();

		// Add rule for multi line comment
		rules.add(new MultiLineRule("--[[", "]]", multilinecomment)); 

		// Add rule for single line comments.
		rules.add(new EndOfLineRule("--", singlelinecomment));

		// Add rule for strings and character constants.
		rules.add(new SingleLineRule("\"", "\"", string, '\\'));
		rules.add(new SingleLineRule("'", "'", string, '\\'));

		// Add rule for multi line string
		rules.add(new MultiLineRule("[[", "]]", multilinestring)); 

		IPredicateRule[] result = new IPredicateRule[rules.size()];
		rules.toArray(result);
		setPredicateRules(result);
	}
}
