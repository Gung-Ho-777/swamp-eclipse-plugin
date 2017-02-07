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

import java.util.Comparator;
import org.continuousassurance.swamp.eclipse.BugDetail;
import org.continuousassurance.swamp.eclipse.Utils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;


/**
 * TableView class is a view that shows a list of bugs and their information.
 * The columns are sortable. Clicking on a bug shows more detailed information
 * about the bug in the Detail view. Double-clicking on the bug jumps to the
 * bug's location in a source file. 
 * @author reid-jr
 *
 */
public class TableView extends ViewPart { 
	
	/**
	 * Names of the columns of the table
	 */
	public static final String[] COLUMN_NAMES = {"File", "Start Line", "End Line", "Bug Type", "Tool", "Platform"};
	/**
	 * Widths of the columns of the table
	 */
	private static final int[] COLUMN_WIDTHS = {400, 50, 50, 400, 200, 200};
	
	private static final String[] COLUMN_TYPES = {Utils.STR_TYPE, Utils.INT_TYPE, Utils.INT_TYPE, Utils.STR_TYPE, Utils.STR_TYPE, Utils.STR_TYPE};
	/**
	 * SWT Table widget
	 */
	private Table table;
	

	
	/**
	 * Constructor for TableView
	 */
	public TableView() {
		super();
	}
	

	
	/**
	 * Getter for table (ideally this wouldn't be exposed but we need it to be
	 * to create rows and add the markers and BugDetail objects to them)
	 * @return table widget
	 */
	public Table getTable() {
		return table;
	}
	
	@Override
	/**
	 * Creates part control
	 * @param parent composite on which widgets will be placed and positioned
	 */
	public void createPartControl(Composite parent) {
		table = Utils.constructTable(parent);
		
		for (int i = 0; i < COLUMN_NAMES.length; i++) {
			Utils.addTableColumn(table, COLUMN_NAMES[i], COLUMN_WIDTHS[i], i, COLUMN_TYPES[i]);
		}
		table.addSelectionListener(new RowSelectionListener(table));
		table.addListener(SWT.MouseDoubleClick, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TableItem[] items = table.getSelection();
				if (items.length > 0) {
					TableItem item = items[0];
					IMarker marker = (IMarker)item.getData(SwampPerspective.MARKER_OBJ);
					IWorkbench wb = PlatformUI.getWorkbench();
					IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
					IWorkbenchPage page = window.getActivePage();
					IEditorPart editor = page.getActiveEditor();
					if ((marker != null) && (editor != null)) {
						IDE.gotoMarker(editor, marker);
					}
				}
			}
		});
	}
	
	@Override
	/**
	 * Sets focus on the table
	 */
	public void setFocus() {
		table.setFocus();
	}
	

	
	/**
	 * Listener for TableItem (i.e. row) selection in a table
	 * @author reid-jr
	 */
	private class RowSelectionListener implements SelectionListener {
		/**
		 * Reference to the table in which the rows (i.e. TableItem objects)
		 * are in
		 */
		private Table table;

		/**
		 * Constructor for RowSelectionListener
		 * @param t table
		 */
		public RowSelectionListener(Table t) {
			table = t;
		}

		@Override
		/**
		 * Updates Detail View when a row gets selected
		 * @param event selection event that occured
		 */
		public void widgetSelected(SelectionEvent event) {
			TableItem[] items = table.getSelection();
			if (items.length > 0) {
				TableItem selectedRow = items[0];
				// TODO: Store a reference to detail view, so we don't keep on having to do this!
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				DetailView view = (DetailView) page.findView(SwampPerspective.DETAIL_VIEW_DESCRIPTOR);
				BugDetail details = (BugDetail)selectedRow.getData(SwampPerspective.BUG_DETAIL_OBJ);
				if (view != null) {
					view.update(details);
				}
			}
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent event) {
		}
		
	}
	
}
