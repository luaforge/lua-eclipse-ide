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

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;

/**
 * This class supplies the keywords of Lua and how to
 * handle a Lua comment.
 * 
 * @author jrodriguez
 *
 */
public class LuaCodeScanner extends RuleBasedScanner {
	private static String[] fgKeywords = {
	    "and", "break", "do", "else", "elseif",
	    "end", "false", "for", "function", "if",
	    "in", "local", "nil", "not", "or", "repeat",
	    "return", "then", "true", "until", "while"};

	public LuaCodeScanner(LuaColorProvider provider) {

		IToken keyword = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.KEYWORD)));
		IToken string = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.STRING)));
		IToken multilinestring = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.MULTI_LINE_STRING)));
		IToken comment = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.SINGLE_LINE_COMMENT)));
		IToken multilinecomment = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.MULTI_LINE_COMMENT)));
		IToken other = new Token(new TextAttribute(provider
				.getColor(LuaColorProvider.DEFAULT)));

		List rules = new ArrayList();

		// Add rule for multi line comments
		rules.add(new MultiLineRule("--[[", "]]", multilinecomment));
		
		// Add rule for single line comments.
		rules.add(new EndOfLineRule("--", comment));
		
		// Add rule for strings.
		rules.add(new SingleLineRule("\"", "\"", string, '\\'));
		rules.add(new SingleLineRule("'", "'", string, '\\'));
		
		// Add rule for multi line strings
		rules.add(new MultiLineRule("[[", "]]", multilinestring));

		// Add generic whitespace rule.
		rules.add(new WhitespaceRule(new LuaWhitespaceDetector()));

		// Add word rule for keywords.
		WordRule wordRule = new WordRule(new LuaWordDetector(), other);
		for (int i = 0; i < fgKeywords.length; i++)
			wordRule.addWord(fgKeywords[i], keyword);
		rules.add(wordRule);

		IRule[] result = new IRule[rules.size()];
		rules.toArray(result);
		setRules(result);

	}
}