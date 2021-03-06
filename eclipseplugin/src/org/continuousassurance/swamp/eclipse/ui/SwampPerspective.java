/*
 * Copyright 2016-2017 Malcolm Reid Jr.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.continuousassurance.swamp.eclipse.ui;

import org.continuousassurance.swamp.eclipse.Activator;
import org.continuousassurance.swamp.eclipse.Controller;
import org.continuousassurance.swamp.eclipse.ResultsRetriever;
import org.continuousassurance.swamp.eclipse.StatusChecker;
import org.continuousassurance.swamp.eclipse.exceptions.ResultsRetrievalException;
import org.continuousassurance.swamp.eclipse.handlers.HandlerUtilityMethods;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IPartService;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Class for implementing SWAMP perspective, which is composed of a Project
 * Explorer view, a custom TableView (shows a list of bugs), and a custom 
 * DetailView (shows a single bug in detail)
 * @author reid-jr
 *
 */
public class SwampPerspective implements IPerspectiveFactory {

	/**
	 * Key for storing IMarker object with row (TableItem)
	 */
	public static final String MARKER_OBJ = "marker";
	
	/**
	 * Key for storing BugDetail object with row (TableItem)
	 */
	public static final String BUG_DETAIL_OBJ = "bugdetail";
	
	/**
	 * ID for this perspective (matches id in plugin.xml)
	 */
	public static final String ID = "org.continuousassurance.swamp.eclipse.ui.perspective";
	
	/**
	 * Constructor for SwampPerspective
	 */
	public SwampPerspective() {
		super();
	}
	
	@Override
	/**
	 * Creates layout of the perspective
	 * @param layout page layout (see Java-doc for more details on what an
	 * 		  IPageLayout is)
	 */
	public void createInitialLayout(IPageLayout layout) {
		System.out.println("Create initial layout invoked!");
		defineLayout(layout);
		Controller.refreshWorkspace();
	}
	
	/**
	 * Places the views in their proper locations and sizes them appropriately
	 * @param layout page layout
	 */
	public void defineLayout(IPageLayout layout) {
		System.out.println("Define initial layout invoked!");
		String editorArea = layout.getEditorArea();
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.LEFT, 0.25f, editorArea);
		topLeft.addView(IPageLayout.ID_PROJECT_EXPLORER);
		IFolderLayout bottom = layout.createFolder("bottom", IPageLayout.BOTTOM, 0.70f, editorArea);
		bottom.addView(TableView.ID);
		bottom.addView(StatusView.ID);
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.60f, editorArea);
		right.addView(DetailView.ID);
		IWorkbench wb = PlatformUI.getWorkbench();
		System.out.println("Attempting to update layout!");
		if (wb != null) {
			IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
			initializeFileChangeListener(window);
		}
	}
	
	/**
	 * Adds a file change listener to the specified window
	 * @param window Eclipse window that the new listener will be registered to
	 */
	public static void initializeFileChangeListener(IWorkbenchWindow window) {
		if (window == null) {
			return;
		}
		IPartService service = window.getPartService();
		service.addPartListener(new FileChangeListener());
	}

	
	/**
	 * Listener for file open in editor being changed.
	 * @author reid-jr
	 *
	 */
	public static class FileChangeListener implements IPartListener2 {

		/* We need to set the rows in the TableView from here. We need to store
		 * markers and BugDetail objects with rows in order to have the desired
		 * behavior (i.e. for us to jump to a specific marker and to have all
		 * the info for a bug when a row is clicked). We have to break
		 * modularity a bit in doing this since we need to set the state of the
		 * TableView from here. Doing it here in the SwampPerspective class
		 * makes the most sense as we can update the editor from here as well.
		 */
	
		/**
		 * Cached reference to the most recent source file that we displayed
		 * results for
		 */
		private IFile currentlyOpenedFile = null;
		
		private IProject currentlyOpenedProject = null;
		
		private boolean statusViewInitialized = false;
		
		/**
		 * Method for refreshing the workspace if the file has changed
		 */
		private void refreshWS(IWorkbenchWindow window) {
			if (window != null) {
				IProject project = HandlerUtilityMethods.getActiveProject(window);
				IFile file = HandlerUtilityMethods.getActiveFile(window);
				if ((project != null) && (file != null)) {
					if (Controller.showAll) {
						if (!project.equals(currentlyOpenedProject)) {
							currentlyOpenedFile = file;
							currentlyOpenedProject = project;
							Controller.refreshWorkspace();
						}
					}
					else {
						if (!file.equals(currentlyOpenedFile)) {
							currentlyOpenedFile = file;
							Controller.refreshWorkspace();
						}
					}
				}
			}
		}
		
		@Override
		/**
		 * Parses SCARF and updates views as appropriate when file is changed
		 * @param part reference to the workbench part
		 */
		public void partActivated(IWorkbenchPartReference part) {
			System.out.println("WorkbenchPartRef: " + part.getId());
			if (!statusViewInitialized && part.getId().equals(StatusView.ID)) {
				statusViewInitialized = true;
				StatusChecker sc = Activator.getStatusChecker();
				if (sc == null || !sc.isRunning()) {
					try {
						ResultsRetriever.retrieveResults();
					} catch (ResultsRetrievalException e) {
						e.printStackTrace();
					}
				}
			}
			else {
				IWorkbenchWindow window = getActiveWindow();
				if (window != null) {
					if ((Controller.getView(window, TableView.ID) != null) || 
							Controller.swampPerspectiveOpen(window.getActivePage())) {
						System.out.println("Swamp Weaknesses View (aka TableView) open");
						refreshWS(window);
					}
				}
			}
		}
		
		@Override
		public void partBroughtToTop(IWorkbenchPartReference arg0) {
		}

		@Override
		public void partClosed(IWorkbenchPartReference arg0) {
		}

		@Override
		public void partDeactivated(IWorkbenchPartReference arg0) {
		}

		@Override
		public void partHidden(IWorkbenchPartReference arg0) {
		}

		@Override
		public void partInputChanged(IWorkbenchPartReference part) {
		}

		@Override
		public void partOpened(IWorkbenchPartReference part) {
			IWorkbenchWindow window = getActiveWindow();
			if (window != null) {
				if ((Controller.getView(window, TableView.ID) != null) || 
						Controller.swampPerspectiveOpen(window.getActivePage())) {
						refreshWS(window);
				}
			}
		}
		
		private static IWorkbenchWindow getActiveWindow() {
			IWorkbench wb = PlatformUI.getWorkbench();
			if (wb != null) {
				IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
				return window;
			}
			return null;
		}

		@Override
		public void partVisible(IWorkbenchPartReference arg0) {
		}
	}
}
