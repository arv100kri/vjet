/*******************************************************************************
 * Copyright (c) 2000-2011 IBM Corporation and others, eBay Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     eBay Inc - modification
 *******************************************************************************/
package org.eclipse.dltk.mod.ui.text.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.dltk.mod.compiler.problem.IProblem;
import org.eclipse.dltk.mod.core.CompletionContext;
import org.eclipse.dltk.mod.core.CompletionProposal;
import org.eclipse.dltk.mod.core.CompletionRequestor;
import org.eclipse.dltk.mod.core.DLTKCore;
import org.eclipse.dltk.mod.core.IModelElement;
import org.eclipse.dltk.mod.core.IScriptProject;
import org.eclipse.dltk.mod.core.ISourceModule;
import org.eclipse.dltk.mod.core.IType;
import org.eclipse.dltk.mod.ui.DLTKUIPlugin;
import org.eclipse.dltk.mod.ui.viewsupport.ImageDescriptorRegistry;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;

/**
 * Script UI implementation of <code>CompletionRequestor</code>. Produces
 * {@link IJavaCompletionProposal}s from the proposal descriptors received via
 * the <code>CompletionRequestor</code> interface.
 * <p>
 * The lifecycle of a <code>CompletionProposalCollector</code> instance is very
 * simple:
 * 
 * <pre>
 *    ISourceModule unit= ...
 *    int offset= ...
 *    
 *    CompletionProposalCollector collector= new CompletionProposalCollector(cu);
 *    unit.codeComplete(offset, collector);
 *    IJavaCompletionProposal[] proposals= collector.getJavaCompletionProposals();
 *    String errorMessage= collector.getErrorMessage();
 *    
 *     / / display  / process proposals
 * </pre>
 * 
 * Note that after a code completion operation, the collector will store any
 * received proposals, which may require a considerable amount of memory, so the
 * collector should not be kept as a reference after a completion operation.
 * </p>
 * <p>
 * Clients may instantiate or subclass.
 * </p>
 * 
 * 
 */
public abstract class ScriptCompletionProposalCollector extends
		CompletionRequestor {
	/** Tells whether this class is in debug mode. */
	private static final boolean DEBUG = "true".equalsIgnoreCase(Platform.getDebugOption("org.eclipse.dltk.mod.ui/debug/ResultCollector")); //$NON-NLS-1$//$NON-NLS-2$

	/** Triggers for method proposals without parameters. Do not modify. */
	/** Triggers for variables. Do not modify. */
	private CompletionProposalLabelProvider fLabelProvider;

	private final ImageDescriptorRegistry fRegistry = DLTKUIPlugin
			.getImageDescriptorRegistry();
	// eBay mod start
	// private final List fScriptProposals = new ArrayList();
	protected final List fScriptProposals = new ArrayList();
	// eBay mod end

	private final List fRawCompletionProposals = new ArrayList();

	private final List fKeywords = new ArrayList();

	// eBay mod start
	// private final Set fSuggestedMethodNames = new HashSet();
	protected final Set fSuggestedMethodNames = new HashSet();
	// eBay mod end

	private final ISourceModule fSourceModule;

	// eBay mod start
	// private final IScriptProject fScriptProject;
	protected final IScriptProject fScriptProject;
	// eBay mod end

	private int fUserReplacementLength;

	private CompletionContext fContext;

	private IProblem fLastProblem;

	/* performance instrumentation */
	private long fStartTime;

	private long fUITime;

	/**
	 * The UI invocation context or <code>null</code>.
	 * 
	 * 
	 */
	private ScriptContentAssistInvocationContext fInvocationContext;

	/**
	 * Creates a new instance ready to collect proposals. If the passed
	 * <code>ISourceModule</code> is not contained in an {@link IScriptProject},
	 * no javadoc will be available as
	 * {@link org.eclipse.jface.text.contentassist.ICompletionProposal#getAdditionalProposalInfo()
	 * additional info} on the created proposals.
	 * 
	 * @param cu
	 *            the compilation unit that the result collector will operate on
	 */
	public ScriptCompletionProposalCollector(ISourceModule cu) {
		this(cu.getScriptProject(), cu);
	}

	/**
	 * Creates a new instance ready to collect proposals. Note that proposals
	 * for anonymous types and method declarations are not created when using
	 * this constructor, as those need to know the compilation unit that they
	 * are created on. Use
	 * {@link ScriptCompletionProposalCollector#CompletionProposalCollector(ISourceModule)}
	 * instead to get all proposals.
	 * <p>
	 * If the passed Script project is <code>null</code>, no javadoc will be
	 * available as
	 * {@link org.eclipse.jface.text.contentassist.ICompletionProposal#getAdditionalProposalInfo()
	 * additional info} on the created (e.g. method and type) proposals.
	 * </p>
	 * 
	 * @param project
	 *            the project that the result collector will operate on, or
	 *            <code>null</code>
	 */
	public ScriptCompletionProposalCollector(IScriptProject project) {
		this(project, null);
	}

	protected ScriptCompletionProposalCollector(IScriptProject project,
			ISourceModule cu) {
		fScriptProject = project;
		fSourceModule = cu;
		fUserReplacementLength = -1;
	}

	/**
	 * Sets the invocation context.
	 * <p>
	 * Subclasses may extend.
	 * </p>
	 * 
	 * @param context
	 *            the invocation context
	 * @see #getInvocationContext()
	 * 
	 */
	public void setInvocationContext(
			ScriptContentAssistInvocationContext context) {
		fInvocationContext = context;
		context.setCollector(this);
	}

	/**
	 * Returns the invocation context. If none has been set via
	 * {@link #setInvocationContext(JavaContentAssistInvocationContext)}, a new
	 * one is created.
	 * 
	 * @return invocationContext the invocation context
	 * 
	 */
	protected final ScriptContentAssistInvocationContext getInvocationContext() {
		if (fInvocationContext == null) {
			setInvocationContext(createScriptContentAssistInvocationContext(getSourceModule()));
		}

		return fInvocationContext;
	}

	// protected abstract ScriptContentAssistInvocationContext
	// createScriptContentAssistInvocationContext(
	// ISourceModule sourceModule);
	// Invocation context
	protected ScriptContentAssistInvocationContext createScriptContentAssistInvocationContext(
			ISourceModule sourceModule) {
		return new ScriptContentAssistInvocationContext(sourceModule) {
			protected CompletionProposalLabelProvider createLabelProvider() {
				return new CompletionProposalLabelProvider();
			}
		};
	}

	protected CompletionProposal[] getRawCompletionProposals() {
		return (CompletionProposal[]) fRawCompletionProposals
				.toArray(new CompletionProposal[fRawCompletionProposals.size()]);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Subclasses may replace, but usually should not need to. Consider
	 * replacing
	 * {@linkplain #createScriptCompletionProposal(CompletionProposal)
	 * createJavaCompletionProposal} instead.
	 * </p>
	 */
	public void accept(CompletionProposal proposal) {
		long start = DEBUG ? System.currentTimeMillis() : 0;
		try {
			if (isFiltered(proposal))
				return;
			fRawCompletionProposals.add(proposal);
			if (proposal.getKind() == CompletionProposal.POTENTIAL_METHOD_DECLARATION) {
				acceptPotentialMethodDeclaration(proposal);
			} else {
				IScriptCompletionProposal scriptProposal = createScriptCompletionProposal(proposal);
				if (scriptProposal != null) {
					fScriptProposals.add(scriptProposal);
					if (proposal.getKind() == CompletionProposal.KEYWORD)
						fKeywords.add(scriptProposal);
				}
			}
		} catch (IllegalArgumentException e) {
			// all signature processing method may throw IAEs
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=84657
			// don't abort, but log and show all the valid proposals
			DLTKUIPlugin
					.log(new Status(
							IStatus.ERROR,
							DLTKUIPlugin.getPluginId(),
							IStatus.OK,
							"Exception when processing proposal for: " + String.valueOf(proposal.getCompletion()), e)); //$NON-NLS-1$
		}
		if (DEBUG)
			fUITime += System.currentTimeMillis() - start;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Subclasses may extend, but usually should not need to.
	 * </p>
	 * 
	 * @see #getContext()
	 */
	public void acceptContext(CompletionContext context) {
		fContext = context;
		this.getLabelProvider().setContext(context);
	}

	// Label provider
	protected CompletionProposalLabelProvider createLabelProvider() {
		return new CompletionProposalLabelProvider();
	}

	protected CompletionProposalLabelProvider getLabelProvider() {
		if (fLabelProvider == null) {
			fLabelProvider = createLabelProvider();
		}
		return this.fLabelProvider;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Subclasses may extend, but must call the super implementation.
	 */
	public void beginReporting() {
		if (DEBUG) {
			fStartTime = System.currentTimeMillis();
			fUITime = 0;
		}
		fLastProblem = null;
		fScriptProposals.clear();
		fKeywords.clear();
		fSuggestedMethodNames.clear();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Subclasses may extend, but must call the super implementation.
	 */
	public void completionFailure(IProblem problem) {
		fLastProblem = problem;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Subclasses may extend, but must call the super implementation.
	 */
	public void endReporting() {
		if (DEBUG) {
			long total = System.currentTimeMillis() - fStartTime;
			System.err.println("Core Collector (core):\t" + (total - fUITime)); //$NON-NLS-1$
			System.err.println("Core Collector (ui):\t" + fUITime); //$NON-NLS-1$
		}
	}

	/**
	 * Returns an error message about any error that may have occurred during
	 * code completion, or the empty string if none.
	 * <p>
	 * Subclasses may replace or extend.
	 * </p>
	 * 
	 * @return an error message or the empty string
	 */
	public String getErrorMessage() {
		if (fLastProblem != null)
			return fLastProblem.getMessage();
		return ""; //$NON-NLS-1$
	}

	/**
	 * Returns the unsorted list of received proposals.
	 * 
	 * @return the unsorted list of received proposals
	 */
	public final IScriptCompletionProposal[] getScriptCompletionProposals() {
		return (IScriptCompletionProposal[]) fScriptProposals
				.toArray(new IScriptCompletionProposal[fScriptProposals.size()]);
	}

	/**
	 * Returns the unsorted list of received keyword proposals.
	 * 
	 * @return the unsorted list of received keyword proposals
	 */
	public final IScriptCompletionProposal[] getKeywordCompletionProposals() {
		return (ScriptCompletionProposal[]) fKeywords
				.toArray(new ScriptCompletionProposal[fKeywords.size()]);
	}

	/**
	 * If the replacement length is set, it overrides the length returned from
	 * the content assist infrastructure. Use this setting if code assist is
	 * called with a none empty selection.
	 * 
	 * @param length
	 *            the new replacement length, relative to the code assist
	 *            offset. Must be equal to or greater than zero.
	 */
	public final void setReplacementLength(int length) {
		fUserReplacementLength = length;
	}

	/**
	 * Computes the relevance for a given <code>CompletionProposal</code>.
	 * <p>
	 * Subclasses may replace, but usually should not need to.
	 * </p>
	 * 
	 * @param proposal
	 *            the proposal to compute the relevance for
	 * @return the relevance for <code>proposal</code>
	 */
	protected int computeRelevance(CompletionProposal proposal) {
		int baseRelevance = proposal.getRelevance() * 16;
		// eBay mod start
		// transfer relevance info for proposal;
		Object obj = proposal.extraInfo;
		if (obj != null || obj instanceof String) {
			String s = (String) obj;
			int index = s.lastIndexOf("-");
			if (index > -1) {
				s = s.substring(index + 1);
			}
			if ("window".equalsIgnoreCase(s.trim())
					|| "Global".equalsIgnoreCase(s.trim())) {
				baseRelevance = 0;
			}
		}
		// eBay mod end
		switch (proposal.getKind()) {
		case CompletionProposal.LABEL_REF:
			return baseRelevance + 0;
		case CompletionProposal.KEYWORD:
			return baseRelevance + 1;
		case CompletionProposal.PACKAGE_REF:
			return baseRelevance + 2;
		case CompletionProposal.TYPE_REF:
			return baseRelevance + 2;
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_NAME_REFERENCE:
		case CompletionProposal.METHOD_DECLARATION:
			return baseRelevance + 3;
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
			return baseRelevance + 3 /* + 99 */;
		case CompletionProposal.FIELD_REF:
			return baseRelevance + 4;
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return baseRelevance + 5;
		default:
			return baseRelevance;
		}
		// eBay mod end

	}

	/**
	 * Creates a new script completion proposal from a core proposal. This may
	 * involve computing the display label and setting up some context.
	 * <p>
	 * This method is called for every proposal that will be displayed to the
	 * user, which may be hundreds. Implementations should therefore defer as
	 * much work as possible: Labels should be computed lazily to leverage
	 * virtual table usage, and any information only needed when
	 * <em>applying</em> a proposal should not be computed yet.
	 * </p>
	 * <p>
	 * Implementations may return <code>null</code> if a proposal should not be
	 * included in the list presented to the user.
	 * </p>
	 * <p>
	 * Subclasses may extend or replace this method.
	 * </p>
	 * 
	 * @param proposal
	 *            the core completion proposal to create a UI proposal for
	 * @return the created script completion proposal, or <code>null</code> if
	 *         no proposal should be displayed
	 */
	protected IScriptCompletionProposal createScriptCompletionProposal(
			CompletionProposal proposal) {
		switch (proposal.getKind()) {
		case CompletionProposal.KEYWORD:
			return createKeywordProposal(proposal);
		case CompletionProposal.PACKAGE_REF:
			return createPackageProposal(proposal);
		case CompletionProposal.TYPE_REF:
			return createTypeProposal(proposal);
			// case CompletionProposal.JAVADOC_TYPE_REF:
			// return createJavadocLinkTypeProposal(proposal);
		case CompletionProposal.FIELD_REF:
			// case CompletionProposal.JAVADOC_FIELD_REF:
			// case CompletionProposal.JAVADOC_VALUE_REF:
			return createFieldProposal(proposal);
		case CompletionProposal.METHOD_REF:
		case CompletionProposal.METHOD_NAME_REFERENCE:
			// case CompletionProposal.JAVADOC_METHOD_REF:
			return createMethodReferenceProposal(proposal);
		case CompletionProposal.METHOD_DECLARATION:
			return createMethodDeclarationProposal(proposal);
		case CompletionProposal.LABEL_REF:
			return createLabelProposal(proposal);
		case CompletionProposal.LOCAL_VARIABLE_REF:
		case CompletionProposal.VARIABLE_DECLARATION:
			return createLocalVariableProposal(proposal);
			// case CompletionProposal.ANNOTATION_ATTRIBUTE_REF:
			// return createAnnotationAttributeReferenceProposal(proposal);
			// case CompletionProposal.JAVADOC_BLOCK_TAG:
			// case CompletionProposal.JAVADOC_PARAM_REF:
			// return createJavadocSimpleProposal(proposal);
			// case CompletionProposal.JAVADOC_INLINE_TAG:
			// return createJavadocInlineTagProposal(proposal);
		case CompletionProposal.POTENTIAL_METHOD_DECLARATION:
		default:
			return null;
		}
	}

	/**
	 * Creates the context information for a given method reference proposal.
	 * The passed proposal must be of kind {@link CompletionProposal#METHOD_REF}
	 * .
	 * 
	 * @param methodProposal
	 *            the method proposal for which to create context information
	 * @return the context information for <code>methodProposal</code>
	 */
	protected final IContextInformation createMethodContextInformation(
			CompletionProposal methodProposal) {
		Assert.isTrue(methodProposal.getKind() == CompletionProposal.METHOD_REF);
		return new ProposalContextInformation(methodProposal);
	}

	/**
	 * Returns the compilation unit that the receiver operates on, or
	 * <code>null</code> if the <code>IScriptProject</code> constructor was used
	 * to create the receiver.
	 * 
	 * @return the compilation unit that the receiver operates on, or
	 *         <code>null</code>
	 */
	protected final ISourceModule getSourceModule() {
		return fSourceModule;
	}

	/**
	 * Returns the <code>CompletionContext</code> for this completion operation.
	 * 
	 * @return the <code>CompletionContext</code> for this completion operation
	 * @see CompletionRequestor#acceptContext(CompletionContext)
	 */
	protected final CompletionContext getContext() {
		return fContext;
	}

	/**
	 * Returns a cached image for the given descriptor.
	 * 
	 * @param descriptor
	 *            the image descriptor to get an image for, may be
	 *            <code>null</code>
	 * @return the image corresponding to <code>descriptor</code>
	 */
	protected final Image getImage(ImageDescriptor descriptor) {
		return (descriptor == null) ? null : fRegistry.get(descriptor);
	}

	/**
	 * Returns the replacement length of a given completion proposal. The
	 * replacement length is usually the difference between the return values of
	 * <code>proposal.getReplaceEnd</code> and
	 * <code>proposal.getReplaceStart</code>, but this behavior may be
	 * overridden by calling {@link #setReplacementLength(int)}.
	 * 
	 * @param proposal
	 *            the completion proposal to get the replacement length for
	 * @return the replacement length for <code>proposal</code>
	 */
	protected final int getLength(CompletionProposal proposal) {
		int start = proposal.getReplaceStart();
		int end = proposal.getReplaceEnd();
		int length;
		if (fUserReplacementLength == -1) {
			length = end - start;
		} else {
			length = fUserReplacementLength;
			// extend length to begin at start
			int behindCompletion = proposal.getCompletionLocation() + 1;
			if (start < behindCompletion) {
				length += behindCompletion - start;
			}
		}
		return length;
	}

	/**
	 * Returns <code>true</code> if <code>proposal</code> is filtered, e.g.
	 * should not be proposed to the user, <code>false</code> if it is valid.
	 * <p>
	 * Subclasses may extends this method. The default implementation filters
	 * proposals set to be ignored via
	 * {@linkplain CompletionRequestor#setIgnored(int, boolean) setIgnored} and
	 * types set to be ignored in the preferences.
	 * </p>
	 * 
	 * @param proposal
	 *            the proposal to filter
	 * @return <code>true</code> to filter <code>proposal</code>,
	 *         <code>false</code> to let it pass
	 */
	protected boolean isFiltered(CompletionProposal proposal) {
		if (isIgnored(proposal.getKind())) {
			return true;
		}

		// TODO: possible add code to check completion preferences for filtering

		return false;
	}

	private void acceptPotentialMethodDeclaration(CompletionProposal proposal) {
		if (fSourceModule == null)
			return;
		// String prefix = String.valueOf(proposal.getName());
		// int completionStart = proposal.getReplaceStart();
		// int completionEnd = proposal.getReplaceEnd();
		// int relevance = computeRelevance(proposal);
		try {
			IModelElement element = fSourceModule.getElementAt(proposal
					.getCompletionLocation() + 1);
			if (element != null) {
				IType type = (IType) element.getAncestor(IModelElement.TYPE);
				if (type != null) {
					// GetterSetterCompletionProposal.evaluateProposals(type,
					// prefix, completionStart, completionEnd
					// - completionStart, relevance + 1,
					// fSuggestedMethodNames, fJavaProposals);
					// MethodCompletionProposal.evaluateProposals(type, prefix,
					// completionStart, completionEnd - completionStart,
					// relevance, fSuggestedMethodNames, fJavaProposals);
					if (DLTKCore.DEBUG) {
						System.out
								.println("TODO: Add method completion proposal support here..."); //$NON-NLS-1$
					}
				}
			}
		} catch (CoreException e) {
			DLTKUIPlugin.log(e);
		}
	}

	// private IScriptCompletionProposal
	// createAnnotationAttributeReferenceProposal(
	// CompletionProposal proposal) {
	// String displayString = getLabelProvider()
	// .createLabelWithTypeAndDeclaration(proposal);
	// ImageDescriptor descriptor = getLabelProvider()
	// .createMethodImageDescriptor(proposal);
	// String completion = String.valueOf(proposal.getCompletion());
	// return createScriptCompletionProposal(completion, proposal
	// .getReplaceStart(), getLength(proposal), getImage(descriptor),
	// displayString, computeRelevance(proposal));
	// }

	protected abstract ScriptCompletionProposal createScriptCompletionProposal(
			String completion, int replaceStart, int length, Image image,
			String displayString, int i);

	protected abstract ScriptCompletionProposal createScriptCompletionProposal(
			String completion, int replaceStart, int length, Image image,
			String displayString, int i, boolean isInDoc);

	protected abstract ScriptCompletionProposal createOverrideCompletionProposal(
			IScriptProject scriptProject, ISourceModule compilationUnit,
			String name, String[] paramTypes, int start, int length,
			String label, String string);

	// eBay mod start
	// private IScriptCompletionProposal createFieldProposal
	protected IScriptCompletionProposal createFieldProposal(
	// eBay mod end
			CompletionProposal proposal) {
		String completion = String.valueOf(proposal.getCompletion());
		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		String label = getLabelProvider().createLabelWithTypeAndDeclaration(
				proposal);
		Image image = getImage(getLabelProvider().createFieldImageDescriptor(
				proposal));
		int relevance = computeRelevance(proposal);
		// CompletionContext context = getContext();
		ScriptCompletionProposal scriptProposal = createScriptCompletionProposal(
				completion, start, length, image, label, relevance, /*
																	 * context
																	 * .isInDoc
																	 * ()
																	 */false);
		if (fScriptProject != null)
			scriptProposal.setProposalInfo(new FieldProposalInfo(
					fScriptProject, proposal));
		scriptProposal.setTriggerCharacters(getVarTrigger());
		return scriptProposal;
	}

	protected IScriptCompletionProposal createKeywordProposal(
			CompletionProposal proposal) {
		String completion = String.valueOf(proposal.getCompletion());
		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		String label = getLabelProvider().createKeywordLabel(proposal);
		Image img = getImage(getLabelProvider().createImageDescriptor(proposal));
		int relevance = computeRelevance(proposal);
		ScriptCompletionProposal scriptProposal = createScriptCompletionProposal(
				completion, start, length, img, label, relevance);

		if (fScriptProject != null) {
			scriptProposal.setProposalInfo(new ProposalInfo(fScriptProject,
					new String(proposal.getName())));
		}

		return scriptProposal;
	}

	protected IScriptCompletionProposal createPackageProposal(
			CompletionProposal proposal) {
		String completion = String.valueOf(proposal.getCompletion());
		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		String label = getLabelProvider().createSimpleLabel(proposal);
		int relevance = computeRelevance(proposal);
		return createScriptCompletionProposal(completion, start, length,
				getImage(getLabelProvider().createImageDescriptor(proposal)),
				label, relevance);
	}

	private IScriptCompletionProposal createLabelProposal(
			CompletionProposal proposal) {
		String completion = String.valueOf(proposal.getCompletion());
		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		String label = getLabelProvider().createSimpleLabel(proposal);
		int relevance = computeRelevance(proposal);
		return createScriptCompletionProposal(completion, start, length, null,
				label, relevance);
		// return null;
	}

	private IScriptCompletionProposal createLocalVariableProposal(
			CompletionProposal proposal) {
		String completion = String.valueOf(proposal.getCompletion());
		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		Image image = getImage(getLabelProvider().createLocalImageDescriptor(
				proposal));
		String label = getLabelProvider().createSimpleLabelWithType(proposal);
		int relevance = computeRelevance(proposal);
		final ScriptCompletionProposal javaProposal = createScriptCompletionProposal(
				completion, start, length, image, label, relevance);
		javaProposal.setTriggerCharacters(getVarTrigger());
		return javaProposal;
	}

	protected abstract char[] getVarTrigger();

	private IScriptCompletionProposal createMethodDeclarationProposal(
			CompletionProposal proposal) {
		if (fSourceModule == null || fScriptProject == null) {
			return null;
		}

		String name = String.valueOf(proposal.getName());

		String[] paramTypes;

		paramTypes = new String[0];

		int start = proposal.getReplaceStart();
		int length = getLength(proposal);
		String label = getLabelProvider().createOverrideMethodProposalLabel(
				proposal);
		ScriptCompletionProposal scriptProposal = createOverrideCompletionProposal(
				fScriptProject, fSourceModule, name, paramTypes, start, length,
				label, String.valueOf(proposal.getCompletion()));
		scriptProposal.setImage(getImage(getLabelProvider()
				.createMethodImageDescriptor(proposal)));

		ProposalInfo info = new MethodProposalInfo(fScriptProject, proposal);
		// info.setHackMessage("<h1></h1>");
		scriptProposal.setProposalInfo(info);

		scriptProposal.setRelevance(computeRelevance(proposal));
		fSuggestedMethodNames.add(name);
		return scriptProposal;
	}

	// eBay MOD START
	// private IScriptCompletionProposal createMethodReferenceProposal(
	// CompletionProposal methodProposal) {
	protected IScriptCompletionProposal createMethodReferenceProposal(
			CompletionProposal methodProposal) {
		// eBay MOD END
		LazyScriptCompletionProposal proposal = new ScriptMethodCompletionProposal(
				methodProposal, getInvocationContext());

		// eBay MOD START
		if (methodProposal.getReplaceStart() == methodProposal.getReplaceEnd()) {
			// proposal.setReplacementLength(0);
			proposal.setReplacementString("");
		} else {
			adaptLength(proposal, methodProposal);
		}
		// adaptLength(proposal, methodProposal);
		// eBay MOD END
		return proposal;
	}

	// eBay MOD START
	// private void adaptLength(LazyScriptCompletionProposal proposal,
	protected void adaptLength(LazyScriptCompletionProposal proposal,
	// eBay MOD END
			CompletionProposal coreProposal) {
		if (fUserReplacementLength != -1) {
			proposal.setReplacementLength(getLength(coreProposal));
		}
	}

	private IScriptCompletionProposal createTypeProposal(
			CompletionProposal typeProposal) {

		String completion = new String(typeProposal.getCompletion());
		int replaceStart = typeProposal.getReplaceStart();
		int length = typeProposal.getReplaceEnd()
				- typeProposal.getReplaceStart() + 1;
		Image image = getImage(getLabelProvider().createTypeImageDescriptor(
				typeProposal));

		String displayString = getLabelProvider().createTypeProposalLabel(
				typeProposal);

		ScriptCompletionProposal scriptProposal = createScriptCompletionProposal(
				completion, replaceStart, length, image, displayString, 0);

		scriptProposal.setRelevance(computeRelevance(typeProposal));
		scriptProposal.setProposalInfo(new TypeProposalInfo(fScriptProject,
				typeProposal));
		return scriptProposal;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.dltk.mod.core.CompletionRequestor#isContextInformationMode()
	 */
	public boolean isContextInformationMode() {
		return fInvocationContext != null
				&& fInvocationContext.isContextInformationMode();
	}
}
