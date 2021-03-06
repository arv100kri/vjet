/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 
 *******************************************************************************/
package org.eclipse.dltk.mod.dbgp.commands;

import java.util.Map;

import org.eclipse.dltk.mod.dbgp.IDbgpProperty;
import org.eclipse.dltk.mod.dbgp.exceptions.DbgpException;

public interface IDbgpContextCommands {
	int LOCAL_CONTEXT_ID = 0;
	int GLOBAL_CONTEXT_ID = 1;
	int CLASS_CONTEXT_ID = 2;

	Map getContextNames(int stackDepth) throws DbgpException;

	IDbgpProperty[] getContextProperties(int stackDepth) throws DbgpException;

	IDbgpProperty[] getContextProperties(int stackDepth, int contextId)
			throws DbgpException;
}
