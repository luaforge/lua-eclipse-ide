/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
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
 *     Retarget to Lua.
 *     Added multiple textboxes to make ports, exes, etc configurable
******************************************************************************/
package org.eclipse.debug.examples.ui.pda.launcher;


import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.examples.core.pda.DebugCorePlugin;
import org.eclipse.debug.examples.ui.pda.DebugUIPlugin;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ResourceListSelectionDialog;


/**
 * Tab to specify the PDA program to run/debug.
 */
public class PDAMainTab extends AbstractLaunchConfigurationTab {
	
	private Text fProgramText;
	private Button fProgramButton;
	private Text fLdbFullPathText;
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Font font = parent.getFont();
		
		Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		GridLayout topLayout = new GridLayout();
		topLayout.verticalSpacing = 0;
		topLayout.numColumns = 3;
		comp.setLayout(topLayout);
		comp.setFont(font);
		
		createVerticalSpacer(comp, 3);
		
		Label programLabel = new Label(comp, SWT.NONE);
		programLabel.setText("&Lua Program:");
		GridData gd = new GridData(GridData.BEGINNING);
		programLabel.setLayoutData(gd);
		programLabel.setFont(font);
		
		fProgramText = new Text(comp, SWT.SINGLE | SWT.BORDER);
		gd = new GridData(GridData.FILL_HORIZONTAL);
		fProgramText.setLayoutData(gd);
		fProgramText.setFont(font);
		fProgramText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateLaunchConfigurationDialog();
			}
		});
		
		fProgramButton = createPushButton(comp, "&Browse...", null); //$NON-NLS-1$
		fProgramButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				browsePDAFiles();
			}
		});

		Label ldbLocationLabel = new Label(comp, SWT.NONE);
		ldbLocationLabel.setText("&open-ldb Full Path:");
		GridData ldbLocationGd = new GridData(GridData.BEGINNING);
		ldbLocationLabel.setLayoutData(ldbLocationGd);
		ldbLocationLabel.setFont(font);

		fLdbFullPathText = new Text(comp, SWT.SINGLE | SWT.BORDER);
		ldbLocationGd = new GridData(GridData.FILL_HORIZONTAL);
		fLdbFullPathText.setLayoutData(ldbLocationGd);
		fLdbFullPathText.setFont(font);
		fLdbFullPathText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateLaunchConfigurationDialog();
			}
		});

	}
	
	/**
	 * Open a resource chooser to select a PDA program 
	 */
	protected void browsePDAFiles() {
		ResourceListSelectionDialog dialog = new ResourceListSelectionDialog(getShell(), ResourcesPlugin.getWorkspace().getRoot(), IResource.FILE);
		dialog.setTitle("PDA Program");
		dialog.setMessage("Select PDA Program");
		if (dialog.open() == Window.OK) {
			Object[] files = dialog.getResult();
			IFile file = (IFile) files[0];
			fProgramText.setText(file.getFullPath().toString());
		}
		
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.
	 * debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		String ldbIpAddress = null;
		try {
			ldbIpAddress = configuration.getAttribute(
					DebugCorePlugin.ATTR_LDB_IP_ADDRESS, (String) null);
			if (ldbIpAddress == null) {
				// usually runs on the same machine
				configuration.setAttribute(DebugCorePlugin.ATTR_LDB_IP_ADDRESS, "127.0.0.1");
			}
		} catch (CoreException e) {
			setErrorMessage(e.getMessage());
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void initializeFrom(ILaunchConfiguration configuration) {
		try {
			String program = null;
			program = configuration.getAttribute(DebugCorePlugin.ATTR_PDA_PROGRAM, (String)null);
			if (program != null) {
				fProgramText.setText(program);
			}
			
			String ldbFullPath = null;
			ldbFullPath = configuration.getAttribute(DebugCorePlugin.ATTR_LDB_FULL_PATH, (String)null);
			if (ldbFullPath != null) {
				this.fLdbFullPathText.setText(ldbFullPath);
			}

		} catch (CoreException e) {
			setErrorMessage(e.getMessage());
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		String program = fProgramText.getText().trim();
		if (program.length() == 0) {
			program = null;
		}
		configuration.setAttribute(DebugCorePlugin.ATTR_PDA_PROGRAM, program);
		
		String ldbFullPath = this.fLdbFullPathText.getText().trim();
		configuration.setAttribute(DebugCorePlugin.ATTR_LDB_FULL_PATH, ldbFullPath);

		// perform resource mapping for contextual launch
		IResource[] resources = null;
		if (program!= null) {
			IPath path = new Path(program);
			IResource res = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
			if (res != null) {
				resources = new IResource[]{res};
			}
		}
		configuration.setMappedResources(resources);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getName()
	 */
	public String getName() {
		return "Main";
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#isValid(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public boolean isValid(ILaunchConfiguration launchConfig) {
		setErrorMessage(null);
		setMessage(null);
		String text = fProgramText.getText();
		if (text.length() > 0) {
			IPath path = new Path(text);
			if (ResourcesPlugin.getWorkspace().getRoot().findMember(path) == null) {
				setErrorMessage("Specified Lua script does not exist");
				return false;
			}
		} else {
			setMessage("Specify a program");
		}

		text = this.fLdbFullPathText.getText();
		if (text.length() > 0) {
			File fil = new File(text);
			if (!fil.exists()) {
				setErrorMessage("Specified ldb program does not exist");
				return false;
			}
		} else {
			setMessage("Specify a full ldb path");
		}

		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getImage()
	 */
	public Image getImage() {
		return DebugUIPlugin.getDefault().getImageRegistry().get(DebugUIPlugin.IMG_OBJ_PDA);
	}
}
