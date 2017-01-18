/*
 * Copyright 2016 Malcolm Reid Jr.
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
package org.continuousassurance.swamp.eclipse.handlers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.continuousassurance.swamp.eclipse.Activator;
import org.continuousassurance.swamp.eclipse.ResultsUtils;
import org.continuousassurance.swamp.eclipse.SwampSubmitter;
import org.continuousassurance.swamp.eclipse.Utils;
import org.continuousassurance.swamp.eclipse.dialogs.AuthenticationDialog;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.resources.File;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;

import edu.uiuc.ncsa.swamp.api.AssessmentRecord;
import edu.wisc.cs.swamp.SwampApiWrapper;

/**
 * Handler for checking for results
 * @author reid-jr
 *
 */
public class CheckResultsHandler extends AbstractHandler {

	private IWorkbenchWindow window;
	SwampApiWrapper api = null;
	
	public CheckResultsHandler() {
		window = null;
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String message = "Got results\n";
		window = HandlerUtil.getActiveWorkbenchWindow(event);
		MessageDialog.open(MessageDialog.CONFIRM, window.getShell(), "Results", message, SWT.NONE);
		checkForResults();
		// Eventually this will be refactored into some sort of SwampResultsHandler but for now, we'll do it all here
		// need to be logged in
		// then we can fetch - keep some persistent object with the Set<String> of assessment ids                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    
		return null;
	}
	
	private void checkForResults() {
		try {
			api = new SwampApiWrapper(SwampApiWrapper.HostType.CUSTOM, Activator.getLastHostname());
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		try {
			if (!api.restoreSession()) {
				// Add authentication dialog here
				if (!authenticateUser()) {
					return;
				}
			}
		} catch (Exception e) {
			if (!authenticateUser()) {
				return;
			}
		}
		Map<String, Set<String>> unfinishedMap = Activator.getUnfinishedAssessments();
		for (String projectID : unfinishedMap.keySet()) {
			Set<String> set = unfinishedMap.get(projectID);
			for (String assessID : set) {
				// TODO: Get SwampApiWrapper instance
				// TODO: Construct filepath
				String id = projectID + "-" + assessID;
				AssessmentRecord record = api.getAssessmentRecord(projectID, assessID);
				System.out.println("Status: " + record.getStatus());
				if (record.getStatus().equals("Finished")) {
					String filepath = ResultsUtils.constructFilepath(projectID, record.getPackageUUID(), record.getToolUUID(), record.getPlatformUUID());
					java.io.File f = new java.io.File(filepath);
					if (f.exists()) {
						f.delete();
					}
					api.getAssessmentResults(projectID, record.getAssessmentResultUUID(), filepath);
					System.out.println("Found results for " + id);
					System.out.println("Written to filepath: " + filepath);
					Activator.finish(projectID, assessID);
				}
				else {
					System.out.println("Still waiting on " + id);
				}
			}
		}
	}
	
	/* TODO: This is actually code from SwampSubmitter -- need to refactor it! */
	private boolean authenticateUser() {
		AuthenticationDialog ad = new AuthenticationDialog(window.getShell(), new MessageConsoleStream(new MessageConsole("SWAMP Results", null)));
		ad.create();
		if (ad.open() != Window.OK) {
			return false;
		}
		api = ad.getSwampApiWrapper();
		Activator.setLoggedIn(true);
		return true;
	}
	


}