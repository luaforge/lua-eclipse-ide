/*******************************************************************************
 * Copyright (c) 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Bjorn Freeman-Benson - initial API and implementation
 *******************************************************************************/
/******************************************************************************
 * Changes:
 *     Copyright (c) 2008 VeriSign, Inc. All rights reserved.
 *     John Rodriguez
 *     Launch ldb program which will start a Lua interpreter
 *     Ports are configurable to pass to ldb listeners
 *     Added a command port to pass commands to ldb, e.g. "step"
 *     Added an event port to get state back from ldb, e.g. suspended at breakpoint
******************************************************************************/
package org.eclipse.debug.examples.core.pda.launcher;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.net.ServerSocket;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.examples.core.pda.DebugCorePlugin;
import org.eclipse.debug.examples.core.pda.model.PDADebugTarget;

/**
 * Launches ldb program starting a Lua interpreter executable written in C
 */
public class PDALaunchDelegate extends LaunchConfigurationDelegate {
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.
	 * eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.debug.core.ILaunch,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {

		List commandList = new ArrayList();

		// cleanup the old markers first
		int depth = IResource.DEPTH_INFINITE;
		try {
			ResourcesPlugin.getWorkspace().getRoot().deleteMarkers(
					IMarker.PROBLEM, true, depth);
		} catch (CoreException e) {
			// something went wrong
			System.out.println("Could not delete markers: " + e);
		}

		// build a command like:
		// ldb -n ../../sort.lua -b true -t ide -c 3000 -e 3001
		// which means
		// ldb lua program, buffer in memory, IDE vs. command line ldb, command
		// port 3000, event port 3001

		// ldb executable
		String ldb = configuration.getAttribute(
				DebugCorePlugin.ATTR_LDB_FULL_PATH, (String) null);
		if (ldb == null) {
			abort(
					"ldb executable location undefined. Check value of ${perlExecutable}.",
					null);
		}
		File exe = new File(ldb);
		if (!exe.exists()) {
			abort(MessageFormat.format(
				"Specified ldb executable {0} does not exist. Check value of ldb.",
				new String[] { ldb }), null);
		}
		commandList.add(ldb);

		// program name
		String program = configuration.getAttribute(
				DebugCorePlugin.ATTR_PDA_PROGRAM, (String) null);
		if (program == null) {
			abort("Lua program unspecified.", null);
		}

		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(
				new Path(program));
		if (!file.exists()) {
			abort(MessageFormat.format("Lua program {0} does not exist.",
					new String[] { file.getFullPath().toString() }), null);
		}

		commandList.add("-n ");
		commandList.add(file.getLocation().toOSString());

		// for the IDE we always buffer in memory after reading the script
		// from disk
		commandList.add("-b ");
		commandList.add("true");

		// this makes ldb work with sockets instead of stdin/stdout
		commandList.add("-t ");
		commandList.add("ide");

		System.out.println("program=" + file.getLocation().toOSString());

		// if in debug mode, add debug arguments - i.e. '-debug requestPort
		// eventPort'
		int requestPort = -1;
		int eventPort = -1;
		if (mode.equals(ILaunchManager.DEBUG_MODE)) {
			requestPort = findFreePort();
			eventPort = findFreePort();
			if (requestPort == -1 || eventPort == -1) {
				abort("Unable to find free port", null);
			}
		}

		commandList.add("-c ");
		commandList.add("" + requestPort);
		commandList.add("-e ");
		commandList.add("" + eventPort);

		String[] commandLine = (String[]) commandList
				.toArray(new String[commandList.size()]);
		System.out.println("commandList=" + commandList);
		Process process = DebugPlugin.exec(commandLine, null);
		IProcess p = DebugPlugin.newProcess(launch, process, ldb);

		// if in debug mode, create a debug target
		if (mode.equals(ILaunchManager.DEBUG_MODE)) {
			IDebugTarget target = new PDADebugTarget(launch, p, requestPort,
					eventPort);
			launch.addDebugTarget(target);
		}
	}
	
	/**
	 * Throws an exception with a new status containing the given
	 * message and optional exception.
	 * 
	 * @param message error message
	 * @param e underlying exception
	 * @throws CoreException
	 */
	private void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, DebugCorePlugin.getDefault().getDescriptor().getUniqueIdentifier(), 0, message, e));
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#buildForLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor) throws CoreException {
		return false;
	}	
	
	/**
	 * Returns a free port number on localhost, or -1 if unable to find a free port.
	 * 
	 * @return a free port number on localhost, or -1 if unable to find a free port
	 */
	public static int findFreePort() {
		ServerSocket socket= null;
		try {
			socket= new ServerSocket(0);
			return socket.getLocalPort();
		} catch (IOException e) { 
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		return -1;		
	}
}
