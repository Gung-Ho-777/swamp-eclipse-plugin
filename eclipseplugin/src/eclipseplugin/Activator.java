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

package eclipseplugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import edu.wisc.cs.swamp.SwampApiWrapper;
import edu.wisc.cs.swamp.exceptions.SessionExpiredException;


/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "eclipseplugin"; //$NON-NLS-1$
	
	private static String hostname;
	
	// Logged into SWAMP
	private static boolean loggedIn;

	// The shared instance
	private static Activator plugin;
	
	private static final String DEFAULT_HOST = "https://www.mir-swamp.org";
	
	private static final String HOST_FILENAME = ".host";
	private static final String UNFINISHED_ASSESS_FILENAME = ".unfinished_assess";
	
	private static Map<String, Set<String>> assessIDs;
	private static Map<String, Set<String>> finishedIDs;
	
	
	/**
	 * The constructor
	 */
	public Activator() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		// (1) get last hostname from file
		String hostnamePath = getHostnamePath();
		File file = new File(hostnamePath);
		SwampApiWrapper api;
		loggedIn = false;
		hostname = DEFAULT_HOST;
		assessIDs = new HashMap<>();
		finishedIDs = new HashMap<>();
		
		if (file.exists()) {
			FileReader filereader = null;
			BufferedReader reader = null;
		
			try {
				filereader = new FileReader(file);
				reader = new BufferedReader(filereader);
				String host = reader.readLine();
				reader.close();
				if (!host.equals("")) {
					api = new SwampApiWrapper(SwampApiWrapper.HostType.CUSTOM, host);
					if (api != null) {
						hostname = host;
						loggedIn = api.restoreSession();
					}
				}
			} catch (Exception e) {
			}
		}
		
		File f = new File(getUnfinishedAssessmentsPath());
		if (f.exists()) {
			FileReader filereader = null;
			BufferedReader reader = null;
			try {
				filereader = new FileReader(f);
				reader = new BufferedReader(filereader);
				String str = reader.readLine();
				while (str != null && !str.equals("")) {
					String[] array = str.split(":");
					String prjID = array[0];
					String assessID = array[1];
					Set<String> set;
					if (assessIDs.containsKey(prjID)) {
						set = assessIDs.get(prjID);
						set.add(assessID);
					}
					else {
						set = new HashSet<>();
						set.add(assessID);
					}
					assessIDs.put(prjID, set);
				}
				
			} catch (IOException e) {
				System.err.println("Unable to read in assessments from file.");
				e.printStackTrace();
			} finally {
				filereader.close();
				reader.close();
			}
		}
	}
	
	public static void setHostname(String name) {
		hostname = name;
		File f = new File(getHostnamePath());
		if (f.exists()) {
			f.delete();
			try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// Write it to file here
		FileWriter filewriter = null;
		BufferedWriter writer = null;
		
		try {
			filewriter = new FileWriter(f);
			writer = new BufferedWriter(filewriter);
			writer.write(name);
			writer.close();
		}
		catch (Exception e) {
			System.err.println("Unable to serialize file");
			e.printStackTrace();
		}
	}
	
	private static String getHostnamePath() {
		return SwampApiWrapper.SWAMP_DIR_PATH + System.getProperty("file.separator") + HOST_FILENAME;
	}
	
	private static String getUnfinishedAssessmentsPath() {
		return SwampApiWrapper.SWAMP_DIR_PATH + System.getProperty("file.separator") + UNFINISHED_ASSESS_FILENAME;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		saveResults();
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}
	
	public static boolean getLoggedIn() {
		return loggedIn;
	}
	
	public static void setLoggedIn(boolean loggedIn) {
		Activator.loggedIn = loggedIn;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
	
	public static String getLastHostname() {
		return hostname;
	}
	
	public static void saveResults() {
		String path = getUnfinishedAssessmentsPath();
		File f = new File(path);
		if (f.exists()) {
			f.delete();
		}
		FileWriter filewriter = null;
		BufferedWriter writer = null;
		
		try {
			filewriter = new FileWriter(f);
			writer = new BufferedWriter(filewriter);
			for (String prjID : assessIDs.keySet()) {
				for (String assessID : assessIDs.get(prjID)) {
					writer.write(prjID + ":" + assessID);
				}
			}
			filewriter.close();
			writer.close();
			
		} catch (Exception e) {
			System.err.println("Unabled to write results file");
			e.printStackTrace();
		} 
	}
	
	private static void addAssessment(Map<String, Set<String>> map, String projectID, String assessID) {
		if (map.containsKey(projectID)) {
			map.get(projectID).add(assessID);
		}
		else {
			Set<String> set = new HashSet<>();
			set.add(assessID);
			map.put(projectID, set);
		}
	}
	
	public static void finish(String projectID, String assessID) {
		if (assessIDs.containsKey(projectID)) {
			Set<String> set = assessIDs.get(projectID);
			if (set.contains(assessID)) {
				set.remove(assessID);
			}
		}
		addAssessment(finishedIDs, projectID, assessID);
	}
	
	public static void addAssessment(String projectID, String assessID) {
		addAssessment(assessIDs, projectID, assessID);
	}
	
	public static Map<String, Set<String>> getFinishedAssessments() {
		return new HashMap<String, Set<String>>(finishedIDs);
	}
	
	public static Map<String, Set<String>> getUnfinishedAssessments() {
		return new HashMap<String, Set<String>>(assessIDs);
	}
}
