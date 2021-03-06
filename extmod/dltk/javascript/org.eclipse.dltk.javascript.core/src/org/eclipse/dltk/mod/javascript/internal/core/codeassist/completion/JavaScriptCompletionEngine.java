/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.mod.javascript.internal.core.codeassist.completion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.dltk.mod.codeassist.IAssistParser;
import org.eclipse.dltk.mod.codeassist.RelevanceConstants;
import org.eclipse.dltk.mod.codeassist.ScriptCompletionEngine;
import org.eclipse.dltk.mod.compiler.CharOperation;
import org.eclipse.dltk.mod.compiler.env.ISourceModule;
import org.eclipse.dltk.mod.core.CompletionContext;
import org.eclipse.dltk.mod.core.CompletionProposal;
import org.eclipse.dltk.mod.core.DLTKCore;
import org.eclipse.dltk.mod.core.IAccessRule;
import org.eclipse.dltk.mod.core.IField;
import org.eclipse.dltk.mod.core.IMethod;
import org.eclipse.dltk.mod.core.IModelElement;
import org.eclipse.dltk.mod.core.IType;
import org.eclipse.dltk.mod.core.ModelException;
import org.eclipse.dltk.mod.core.mixin.IMixinElement;
import org.eclipse.dltk.mod.core.mixin.MixinModel;
import org.eclipse.dltk.mod.internal.javascript.reference.resolvers.ReferenceResolverContext;
import org.eclipse.dltk.mod.internal.javascript.reference.resolvers.SelfCompletingReference;
import org.eclipse.dltk.mod.internal.javascript.typeinference.CombinedOrReference;
import org.eclipse.dltk.mod.internal.javascript.typeinference.HostCollection;
import org.eclipse.dltk.mod.internal.javascript.typeinference.IClassReference;
import org.eclipse.dltk.mod.internal.javascript.typeinference.IReference;
import org.eclipse.dltk.mod.javascript.core.JavaScriptKeywords;
import org.eclipse.dltk.mod.javascript.internal.core.codeassist.AssitUtils;
import org.eclipse.dltk.mod.javascript.internal.core.codeassist.AssitUtils.PositionCalculator;
import org.eclipse.dltk.mod.javascript.internal.core.mixin.JavaScriptMixinModel;

public class JavaScriptCompletionEngine extends ScriptCompletionEngine {

	private boolean useEngine = true;

	public boolean isUseEngine() {
		return useEngine;
	}

	public void setUseEngine(boolean useEngine) {
		this.useEngine = useEngine;
	}

	public JavaScriptCompletionEngine(/*
										 * ISearchableEnvironment
										 * nameEnvironment, CompletionRequestor
										 * requestor, Map settings,
										 * IScriptProject scriptProject
										 */) {
	}

	protected int getEndOfEmptyToken() {
		return 0;
	}

	protected String processMethodName(IMethod method, String token) {
		return method.getElementName();
	}

	protected String processTypeName(IType method, String token) {
		return null;
	}

	public IAssistParser getParser() {
		return null;
	}

	AssitUtils.PositionCalculator calculator;;

	public void complete(ISourceModule cu, int position, int i) {
		// System.out.println("Completion position:" + position);
		this.actualCompletionPosition = position;
		this.requestor.beginReporting();
		String content = cu.getSourceContents();
		if (position < 0 || position > content.length()) {
			return;
		}
		if (position > 0) {
			if (content.charAt(position - 1) == '.') {
				// special case;
				content = content.substring(0, position) + " \n\r e"
						+ content.substring(position);
			}
			if (position > 0)
				if (content.charAt(position - 1) == '=') {
					// special case;
					content = content.substring(0, position) + " \n\r e"
							+ content.substring(position);
				}
		}
		calculator = new PositionCalculator(content, position, false);
		char[] fileName2 = cu.getFileName();
		ReferenceResolverContext buildContext = AssitUtils.buildContext(
				(org.eclipse.dltk.mod.core.ISourceModule) cu, position, content,
				fileName2);
		HostCollection collection = buildContext.getHostCollection();

		String startPart = calculator.getCompletion();
		this.setSourceRange(position - startPart.length(), position);
		// System.out.println(startPart);
		if (calculator.isMember()) {
			doCompletionOnMember(buildContext, cu, position, content, position,
					collection);
		} else {
			doGlobalCompletion(buildContext, cu, position, position,
					collection, startPart);
		}
		this.requestor.endReporting();
	}

	private void doGlobalCompletion(ReferenceResolverContext buildContext,
			ISourceModule cu, int position, int pos, HostCollection collection,
			String startPart) {
		HashSet completedNames = new HashSet();

		String completion = startPart;
		char[] token = completion.toCharArray();

		HashMap names = new HashMap();
		Set resolveGlobals = buildContext.resolveGlobals(completion);
		Iterator it = resolveGlobals.iterator();
		HashSet classes = new HashSet();
		while (it.hasNext()) {
			Object o = it.next();
			if (o instanceof IReference) {
				IReference r = (IReference) o;
				if (r instanceof IClassReference)
					classes.add(r);
				else {
					putAndCheckDuplicateReference(names, r);
				}
			}
		}
		names.remove("!!!returnValue");
		completeFromMap(position, completion, names);
		if (names.size() > 0) {
			char[][] choices = new char[names.size()][];
			int ia = 0;
			for (Iterator iterator = names.keySet().iterator(); iterator
					.hasNext();) {
				String name = (String) iterator.next();
				choices[ia] = name.toCharArray();
				ia++;
			}
			findLocalVariables(token, choices, true, false);
		}
		if (classes.size() > 0) {
			char[][] choices = new char[classes.size()][];
			int ia = 0;
			for (Iterator iterator = classes.iterator(); iterator.hasNext();) {
				IReference name = (IReference) iterator.next();
				choices[ia] = name.getName().toCharArray();
				ia++;
			}
			findElements(token, choices, true, false,
					CompletionProposal.TYPE_REF);
		}

		if (useEngine) {
			doCompletionOnKeyword(position, pos, completion);
			JavaScriptMixinModel instance = JavaScriptMixinModel.getInstance();
			String[] findElements = instance.findElements(MixinModel.SEPARATOR
					+ completion);

			ArrayList methods = new ArrayList();
			ArrayList fields = new ArrayList();

			for (int a = 0; a < findElements.length; a++) {
				String string = findElements[a];
				if (string.lastIndexOf(MixinModel.SEPARATOR) > 0)
					continue;
				IMixinElement mixinElement = instance.getRawModel().get(string);
				if (mixinElement == null)
					continue;
				Object[] allObjects = mixinElement.getObjects(buildContext
						.getModule());
				// Object[] allObjects = mixinElement.getAllObjects();
				if (allObjects.length > 0) {
					for (int i = 0; i < allObjects.length; i++) {
						Object object = allObjects[i];
						if (object instanceof IModelElement) {
							IModelElement el = (IModelElement) object;
							int elementType = el.getElementType();
							if (elementType == IModelElement.METHOD) {
								methods.add(el);
							} else if (elementType == IModelElement.FIELD) {
								String elementName = el.getElementName();
								if (!completedNames.contains(elementName)) {
									fields.add(elementName.toCharArray());
									completedNames.add(elementName);
								}
							}
						} else if (object == null) {
							String lastKeySegment = mixinElement
									.getLastKeySegment();
							if (!completedNames.contains(lastKeySegment)) {
								fields.add(lastKeySegment.toCharArray());
								completedNames.add(lastKeySegment);
							}
						}
					}
				}
			}
			findMethods(token, true, methods);
			char[][] choices = new char[fields.size()][];
			for (int a = 0; a < fields.size(); a++) {
				choices[a] = (char[]) fields.get(a);
			}
			findLocalVariables(token, choices, true, false);
			// doCompletionOnFunction(position, pos, completion);
			// doCompletionOnGlobalVariable(position, pos, completion,
			// completedNames);
		}
		// report parameters and local variables
		Map rfs = collection.getReferences();
		while (collection.getParent() != null) {
			collection = collection.getParent();
			Map m1 = collection.getReferences();
			it = m1.keySet().iterator();
			while (it.hasNext()) {
				Object next = it.next();
				if (!(next instanceof String))
					continue;
				String key = (String) next;
				if (!rfs.containsKey(key))
					rfs.put(key, m1.get(key));
			}

		}
		it = rfs.keySet().iterator();
		while (it.hasNext()) {
			Object next = it.next();
			if (!(next instanceof String))
				continue;
			String name = (String) next;
			if (completedNames.contains(name))
				continue;
			names.put(name, rfs.get(name));
		}
		names.remove("!!!returnValue");
		if (names.size() > 0) {
			completeFromMap(position, completion, names);
		}
	}

	private void doCompletionOnMember(ReferenceResolverContext buildContext,
			ISourceModule cu, int position, String content, int pos,
			HostCollection collection) {

		String completionPart = calculator.getCompletionPart();
		String corePart = calculator.getCorePart();
		final HashMap dubR = new HashMap();
		Set resolveGlobals = buildContext.resolveGlobals(corePart + '.');
		Iterator it = resolveGlobals.iterator();
		while (it.hasNext()) {
			Object o = it.next();
			if (o instanceof IReference) {
				IReference r = (IReference) o;
				// if (r instanceof IClassReference)
				// classes.add(r);
				// else {
				putAndCheckDuplicateReference(dubR, r);
				// }
			}
		}
		completeFromMap(position, completionPart, dubR);

		Set references = collection.queryElements(corePart, true);
		Iterator iterator;
		if (!references.isEmpty()) {
			iterator = references.iterator();
			HashSet fields = new HashSet();
			while (iterator.hasNext()) {
				Object next = iterator.next();
				if (!(next instanceof IReference))
					continue;
				IReference mnext = (IReference) next;
				IReference proto = mnext.getPrototype(true);
				if (proto != null) {
					Collection protos = new LinkedHashSet();
					while (proto != null && !protos.contains(proto)) {
						protos.add(proto);
						proto = proto.getPrototype(false);
					}
					protos = new LinkedList(protos);
					Collections.reverse((List) protos);
					Iterator iterator2 = protos.iterator();
					while (iterator2.hasNext()) {
						IReference proto1 = (IReference) iterator2.next();
						fields.addAll(proto1.getChilds(true));
					}
				}
				fields.addAll(mnext.getChilds(true));
			}

			for (iterator = fields.iterator(); iterator.hasNext();) {
				Object next = iterator.next();
				if (next instanceof IReference) {
					IReference name = (IReference) next;
					String refa = name.getName();
					dubR.put(refa, name);
				}
			}
			completeFromMap(position, completionPart, dubR);
		}
	}

	/**
	 * @param dubR
	 * @param r
	 */
	private void putAndCheckDuplicateReference(final HashMap dubR, IReference r) {
		Object put = dubR.put(r.getName(), r);
		if (put instanceof IReference) {
			if (r instanceof CombinedOrReference) {
				((CombinedOrReference) r)
						.addReference((IReference) put);
			} else if (put instanceof CombinedOrReference) {
				((CombinedOrReference) put).addReference(r);
				dubR.put(r.getName(), put);
			} else {
				CombinedOrReference or = new CombinedOrReference();
				or.addReference(r);
				or.addReference((IReference) put);
				dubR.put(r.getName(), or);
			}
		}
	}

	private void completeFromMap(int position, String completionPart,
			final HashMap dubR) {
		Iterator iterator;
		this.requestor.acceptContext(new CompletionContext());
		this.setSourceRange(position - completionPart.length(), position);
		char[] token = completionPart.toCharArray();
		int length = token.length;
		for (iterator = dubR.values().iterator(); iterator.hasNext();) {
			Object next = iterator.next();

			if (next instanceof SelfCompletingReference) {
				SelfCompletingReference cm = (SelfCompletingReference) next;
				int knd = cm.getKind();

				char[] name = cm.getName().toCharArray();
				if (length <= name.length
						&& CharOperation.prefixEquals(token, name, false)) {
					CompletionProposal createProposal = this.createProposal(
							knd, this.actualCompletionPosition);
					createProposal.setName(name);
					createProposal.setCompletion(name);
					// createProposal.setSignature(name);
					createProposal.extraInfo = cm;
					// createProposal.setDeclarationSignature(cm.getDeclarationSignature());
					// createProposal.setSignature(cm.getSignature());
					createProposal.setReplaceRange(this.startPosition
							- this.offset, this.endPosition - this.offset);
					this.requestor.accept(createProposal);
				}
				iterator.remove();
			} else if (next instanceof IReference) {
				IReference ref = (IReference) next;
				int knd = ref.isFunctionRef() ? CompletionProposal.METHOD_REF
						: CompletionProposal.LOCAL_VARIABLE_REF;
				char[] name = ref.getName().toCharArray();
				if (length <= name.length
						&& CharOperation.prefixEquals(token, name, false)) {
					CompletionProposal createProposal = this.createProposal(
							knd, this.actualCompletionPosition);
					createProposal.setName(name);
					createProposal.setCompletion(name);
					// createProposal.setSignature(name);
					// createProposal.setDeclarationSignature(cm.getDeclarationSignature());
					// createProposal.setSignature(cm.getSignature());
					createProposal.setReplaceRange(this.startPosition
							- this.offset, this.endPosition - this.offset);
					this.requestor.accept(createProposal);
				}
				iterator.remove();
			}
		}
	}

	int computeBaseRelevance() {
		return RelevanceConstants.R_DEFAULT;
	}

	private int computeRelevanceForInterestingProposal() {
		return RelevanceConstants.R_INTERESTING;
	}

	protected int computeRelevanceForRestrictions(int accessRuleKind) {
		if (accessRuleKind == IAccessRule.K_ACCESSIBLE) {
			return RelevanceConstants.R_NON_RESTRICTED;
		}
		return 0;
	}

	protected void findMethods(char[] token, boolean canCompleteEmptyToken,
			List methods, int kind) {
		if (methods == null || methods.size() == 0)
			return;

		int length = token.length;
		String tok = new String(token);
		if (canCompleteEmptyToken || length > 0) {
			for (int i = 0; i < methods.size(); i++) {
				IMethod method = (IMethod) methods.get(i);
				String qname = processMethodName(method, tok);
				char[] name = qname.toCharArray();
				if (DLTKCore.DEBUG_COMPLETION) {
					System.out.println("Completion:" + qname);
				}
				if (length <= name.length
						&& CharOperation.prefixEquals(token, name, false)) {
					int relevance = computeBaseRelevance();
					relevance += computeRelevanceForInterestingProposal();
					relevance += computeRelevanceForCaseMatching(token, name);
					relevance += computeRelevanceForRestrictions(IAccessRule.K_ACCESSIBLE); // no

					// accept result
					noProposal = false;
					if (!requestor.isIgnored(kind)) {
						CompletionProposal proposal = createProposal(kind,
								actualCompletionPosition);
						// proposal.setSignature(getSignature(typeBinding));
						// proposal.setPackageName(q);
						// proposal.setTypeName(displayName);
						String[] arguments = null;

						try {
							arguments = method.getParameters();
						} catch (ModelException e) {

						}
						if (arguments != null && arguments.length > 0) {
							char[][] args = new char[arguments.length][];
							for (int j = 0; j < arguments.length; ++j) {
								args[j] = arguments[j].toCharArray();
							}
							proposal.setParameterNames(args);
						}
						if (kind == CompletionProposal.METHOD_REF) {
							StringBuffer sig = new StringBuffer();
							sig.append(method.getElementName());
							sig.append('(');
							if (arguments != null)
								for (int a = 0; a < arguments.length; a++) {
									sig.append('L');
									sig.append("Object");
									sig.append(';');
								}
							sig.append(')');
							// proposal.setSignature(sig.toString().toCharArray());
						}
						proposal.setName(name);
						proposal.setCompletion(name);
						// proposal.setFlags(Flags.AccDefault);
						proposal.setReplaceRange(this.startPosition
								- this.offset, this.endPosition - this.offset);
						proposal.extraInfo = method;
						proposal.setRelevance(relevance);
						this.requestor.accept(proposal);
						if (DEBUG) {
							this.printDebug(proposal);
						}
					}
				}
			}
		}
	}

	private void doCompletionOnKeyword(int position, int pos, String startPart) {
		String[] keywords = JavaScriptKeywords.getJavaScriptKeywords();
		char[][] keyWordsArray = new char[keywords.length][];
		for (int a = 0; a < keywords.length; a++) {
			keyWordsArray[a] = keywords[a].toCharArray();
		}
		findKeywords(startPart.toCharArray(), keyWordsArray, true);
	}

	protected String processFieldName(IField field, String token) {
		return null;
	}
}
