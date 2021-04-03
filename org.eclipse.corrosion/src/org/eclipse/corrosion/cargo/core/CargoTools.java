/*********************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen   (Red Hat Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.corrosion.cargo.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.corrosion.CorrosionPlugin;
import org.eclipse.corrosion.CorrosionPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class CargoTools {

	private CargoTools() {
		throw new IllegalStateException("Utility class"); //$NON-NLS-1$
	}

	public static List<CLIOption> getOptions(String subCommand) {
		try {
			Process process = CorrosionPlugin.getProcessForCommand(getCargoCommand(), subCommand, "--help"); //$NON-NLS-1$
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line = in.readLine();
				while (line != null) {
					if ("options:".equalsIgnoreCase(line)) { //$NON-NLS-1$
						break;
					}
					line = in.readLine();
				}
				if (line == null) {
					return Collections.emptyList();
				}
				List<CLIOption> options = new ArrayList<>();
				List<String> currentOptionLines = new ArrayList<>();
				while (line != null) {
					if (line.matches("\\s*")) { //$NON-NLS-1$
						break;
					} else if (line.matches("\\s*-+.*")) { //$NON-NLS-1$
						if (!currentOptionLines.isEmpty()) {
							options.add(new CLIOption(currentOptionLines));
							currentOptionLines.clear();
						}
						currentOptionLines.add(line);
					} else if (!currentOptionLines.isEmpty()) {
						currentOptionLines.add(line);
					}
					line = in.readLine();
				}
				if (!currentOptionLines.isEmpty()) {
					options.add(new CLIOption(currentOptionLines));
					currentOptionLines.clear();
				}
				return options;
			}
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	public static String getCargoCommand() {
		IPreferenceStore store = CorrosionPlugin.getDefault().getPreferenceStore();
		return store.getString(CorrosionPreferenceInitializer.CARGO_PATH_PREFERENCE);
	}

	public static void ensureDotCargoImportedAsProject(IProgressMonitor monitor) throws CoreException {
		File cargoFolder = new File(System.getProperty("user.home") + "/.cargo"); //$NON-NLS-1$ //$NON-NLS-2$
		if (!cargoFolder.exists()) {
			return;
		}
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		for (IProject project : workspace.getRoot().getProjects()) {
			if (!project.isOpen() && project.getName().startsWith(".cargo")) { //$NON-NLS-1$
				project.open(monitor);
			}
			IPath location = project.getLocation();
			if (location != null) {
				File projectFolder = location.toFile().getAbsoluteFile();
				if (cargoFolder.getAbsolutePath().startsWith(projectFolder.getAbsolutePath())) {
					return; // .cargo already imported
				}
			}
		}
		// No .cargo folder available in workspace
		String projectName = ".cargo"; //$NON-NLS-1$
		while (workspace.getRoot().getProject(projectName).exists()) {
			projectName += '_';
		}
		IProjectDescription description = workspace.newProjectDescription(projectName);
		description.setLocation(Path.fromOSString(cargoFolder.getAbsolutePath()));
		IProject cargoProject = workspace.getRoot().getProject(projectName);
		cargoProject.create(description, monitor);
		cargoProject.open(monitor);
	}
}
