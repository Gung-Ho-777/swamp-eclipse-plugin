/*
 * Copyright 2017 Malcolm Reid Jr.
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

import org.continuousassurance.swamp.eclipse.AssessmentDetails;
import org.continuousassurance.swamp.eclipse.Utils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.part.ViewPart;

public class StatusView extends ViewPart {

	/**
	 * Names of the columns of the table
	 */
	public static final String[] COLUMN_NAMES = {"SWAMP Package", "Version", "Eclipse Project", "Submission Time", "Status", "Number of Bugs" };
	
	/**
	 * Widths of the columns of the table
	 */
	private static final int[] COLUMN_WIDTHS = {300, 100, 300, 100, 200, 50};
	
	/**
	 * Types (String vs. int) of the columns of the table (this is used for sorting properly)
	 */
	private static final String[] COLUMN_TYPES = {Utils.STR_TYPE, Utils.STR_TYPE, Utils.STR_TYPE, Utils.STR_TYPE, Utils.STR_TYPE, Utils.INT_TYPE};
	
	/**
	 * Label for refresh toolbar button
	 */
	private static final String REFRESH_ACTION_LABEL = "Refresh";
	
	/**
	 * Action for refreshing assessment statuses
	 */
	private Action refreshItemAction;
	
	/**
	 * SWT Table widget
	 */
	private Table table;
	
	/**
	 * Title of error dialog
	 */
	/*
	private static String ERROR_TITLE = "Error";
	*/
	
	/**
	 * Message for user not logged in while attempting to view results status
	 */
	/*
	private static String NOT_LOGGED_IN_MSG = "You are not currently logged into the SWAMP. Please log in before trying to view the status of your assessments.";
	*/
	
	@Override
	public void createPartControl(Composite parent) {
		table = Utils.constructTable(parent);
		
		for (int i = 0; i < COLUMN_NAMES.length; i++) {
			Utils.addTableColumn(table, COLUMN_NAMES[i], COLUMN_WIDTHS[i], i, COLUMN_TYPES[i]);
		}
		
		createActions();
		createToolbar();
	}
	
	private void createActions() {
		refreshItemAction = new Action(REFRESH_ACTION_LABEL) {
			public void run() {
				// TODO: Refresh things here
			}
		};
		// TODO: refreshItemAction.setImageDescriptor("icons/refresh.png");
	}
	
	private void createToolbar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(refreshItemAction);
	}

	@Override
	public void setFocus() {
		table.setFocus();
		/*
		if (Activator.getLoggedIn()) {
			table.setFocus();
		}
		else {
			table.removeAll();
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			MessageDialog.openError(shell, ERROR_TITLE, NOT_LOGGED_IN_MSG);
		}
		*/
	}
	
	public void clearTable() {
		if (table != null) {
			table.removeAll();
		}
	}
	
	public void addRowToStatusTable(String statusStr) {
		TableItem item = new TableItem(table, SWT.NONE);
		String[] parts = statusStr.split(AssessmentDetails.DELIMITER);
		for (int i = 2; i < parts.length; i++) {
			item.setText(i, parts[i]);
		}
	}

}