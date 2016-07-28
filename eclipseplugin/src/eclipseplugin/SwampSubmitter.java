package eclipseplugin;

import java.io.File;
import java.util.Date;
import java.util.HashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.osgi.framework.Version;

import eclipseplugin.dialogs.AuthenticationDialog;
import eclipseplugin.dialogs.ConfigDialog;
import eclipseplugin.dialogs.SelectionDialog;
import edu.uiuc.ncsa.swamp.api.PackageThing;
import edu.uiuc.ncsa.swamp.api.PackageVersion;
import edu.wisc.cs.swamp.SwampApiWrapper;
import edu.wisc.cs.swamp.exceptions.IncompatibleAssessmentTupleException;
import edu.wisc.cs.swamp.exceptions.InvalidIdentifierException;
import edu.wisc.cs.swamp.exceptions.SessionExpiredException;

public class SwampSubmitter {

	private MessageConsoleStream out;
	private SwampApiWrapper api;
	private IWorkbenchWindow window;
	private String configFilepath;

	private static String SWAMP_FAMILY 		= "SWAMP_FAMILY";
	private static String SESSION_STRING 	= "swampsession";
	private static String CONFIG_FILENAME 	= ".swampconfig";
	
	public SwampSubmitter(IWorkbenchWindow window) {
		this.window = window;
		//configFilepath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString() + CONFIG_FILENAME;
	}
	
	private MessageConsoleStream initializeConsole(String consoleName) {
		/* View http://wiki.eclipse.org/FAQ_How_do_I_write_to_the_console_from_a_plug-in%3F for more details */
		/* Adapted from the above link */
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMgr = plugin.getConsoleManager();
		MessageConsole console = new MessageConsole(consoleName, null);
		conMgr.addConsoles(new IConsole[]{console});
		IWorkbenchPage page = window.getActivePage();
		try {
			IConsoleView view = (IConsoleView) page.showView(IConsoleConstants.ID_CONSOLE_VIEW);
			view.display(console);
		} catch (PartInitException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MessageConsoleStream stream = console.newMessageStream();
		printInitialInfo(stream);
		return stream;
	}

	private void runBackgroundJob(SubmissionInfo si, boolean fromFile) {
		
		Job job = new Job("SWAMP Assessment Submission") {
			
			@Override
			public boolean belongsTo(Object family) {
				return family == SWAMP_FAMILY;
			}
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (fromFile) {
					if (!FileSerializer.deserializeSubmissionInfo(configFilepath, si)) {
						// TODO Strengthen error here
						out.println("ERROR: Error in loading from previous assessment found.");
						return Status.CANCEL_STATUS;
					}
				}
				ClasspathHandler classpathHandler = null;
				if (si.needsBuildFile()) {
					out.println("Status: Generating build file");
					classpathHandler = generateBuildFiles(si.getProject());
					if (classpathHandler == null) {
						// TODO Handle this error better
						return Status.CANCEL_STATUS;
					}
				}
				out.println("Status: Packaging Project");
				PackageInfo pkgInfo = packageProject(si.getPackageName(), si.getPackageVersion(), si.getBuildSystem() , si.getBuildDirectory(), si.getBuildFile(), si.getBuildTarget());
				
				out.println("Status: Uploading package to SWAMP");
				String prjUUID = si.getSelectedProjectID();
				String pkgVersUUID = uploadPackage(pkgInfo.getParentPath(), prjUUID, pkgInfo.getArchiveFilename(), si.isNewPackage());
				
				if (classpathHandler != null) {
					classpathHandler.revertClasspath(ResourcesPlugin.getWorkspace().getRoot(), new HashSet<ClasspathHandler>());
				}
				
				// Deletion code - uncomment for release
				/*
				pkg.deleteFiles();
				if (autoGenBuild) {
					System.out.println("Auto-generated build file at " + path + "/build.xml");
					File f = new File(path + "/build.xml");
					if (f != null) {
						if (!f.delete()) {
							System.err.println("Unable to delete auto-generated build file");
						}
					}
				}
				*/
				out.println("Status: Submitting assessments");
				for (String platformUUID : si.getSelectedPlatformIDs()) {
					for (String toolUUID : si.getSelectedToolIDs()) {
						submitAssessment(pkgVersUUID, toolUUID, prjUUID, platformUUID);
					}
				}
				
				return Status.OK_STATUS;
			}
		};
		//job.setRule(new MutexRule());
		job.setUser(true);
		job.schedule();
	}
	
	private ClasspathHandler generateBuildFiles(IProject proj) {
		ClasspathHandler classpathHandler = null;
		// Generating Buildfile
		IJavaProject javaProj = JavaCore.create(proj);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		String rootPath = root.getLocation().toString();
		classpathHandler = new ClasspathHandler(null, javaProj, rootPath);// cd.getPkgPath()); // TODO replace this w/ workspace path
		System.out.println(classpathHandler.getProjectName());
		
		if (classpathHandler.hasCycles()) {
			out.println("Error: There are cyclic dependencies in this project. Please remove all cycles before resubmitting.");
			System.err.println("Huge error. Cyclic dependencies!");
			classpathHandler = null;
			return null;
		}
		BuildfileGenerator.generateBuildFile(classpathHandler);
		System.out.println("Build file generated");
		return classpathHandler;
	}
	
	private PackageInfo packageProject(String packageName, String packageVersion, String buildSystem, String buildDir, String buildFile, String buildTarget) {
		// Zipping and generating package.conf
		Date date = new Date();
		String timestamp = date.toString();
		//String path = cd.getPkgPath();
		String path = ResourcesPlugin.getWorkspace().getRoot().getLocation().toString() + "/package";
		String filename = timestamp + "-" + packageName + ".zip";
		String filenameNoSpaces = filename.replace(" ", "-").replace(":", "").toLowerCase(); // PackageVersionHandler mangles the name for some reason if there are colons or uppercase letters
		System.out.println("Package Name: " + packageName);
		System.out.println("Path: " + path);
		System.out.println("Filename: " + filenameNoSpaces);
		// output name should be some combination of pkg name, version, timestamp, extension (.zip)
		
		PackageInfo pkg = new PackageInfo(path, filenameNoSpaces, packageName); // pass in path and output zip file name
		
		pkg.setPkgShortName(packageName);
		pkg.setVersion(packageVersion);
		pkg.setBuildSys(buildSystem);
		pkg.setBuildDir(buildDir);
		pkg.setBuildFile(buildFile);
		pkg.setBuildTarget(buildTarget);
		
		pkg.writePkgConfFile();

		return pkg;
	}

	private String uploadPackage(String parentDir, String prjUUID, String filename, boolean newPackage) {
		// Upload package
		System.out.println("Uploading package");
		System.out.println("Package-conf directory: " + parentDir + "/package.conf");
		System.out.println("Archive directory: " + parentDir + "/" + filename);
		System.out.println("Project UUID: " + prjUUID);
		String pkgVersUUID = null;
		try {
			pkgVersUUID = api.uploadPackage(parentDir + "/package.conf", parentDir + "/" + filename, prjUUID, newPackage);
		} catch (InvalidIdentifierException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		if (pkgVersUUID == null) {
			// TODO handle error here
			System.err.println("Error in uploading package.");
		}	
		return pkgVersUUID;
	}
	
	// TODO Throw an exception if we can't get here
	private boolean initializeSwampApi() {
		if (api != null) {
			return true;
		}
		try {
			api = new SwampApiWrapper(SwampApiWrapper.HostType.DEVELOPMENT);
		} catch (Exception e) {
			out.println("Error: Unable to initialize SWAMP API.");
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	private static void printInitialInfo(MessageConsoleStream out) {
		Version version = Platform.getBundle("org.eclipse.platform").getVersion();
		String versionStr = "Eclipse";
		if (version != null) {
			versionStr += " " + version.toString();
		}
		else {
			versionStr += " (could not detect version)";
		}
		out.println("Status: Launched SWAMP plugin - running on " + versionStr + ".");
	}
	
	public void launchBackgroundAssessment(String configPath) {
		initializeSwampApi();
		out = initializeConsole("SWAMP Plugin");
		try {
			if (!api.restoreSession(SESSION_STRING)) {
				// launch authentication dialog
				if (!authenticateUser()) {
					return;
				}
			}	
		} catch (SessionExpiredException e) {
				// launch authentication dialog
			if (!authenticateUser()) {
				return;
			}
		}
		if (configPath == null || configPath.equals("")) {
			configFilepath = null;
		}
		else {
			configFilepath = configPath + File.separator + CONFIG_FILENAME;
		}
		SubmissionInfo si = new SubmissionInfo(this.api);
		if ((configFilepath == null) || (!new File(configFilepath).exists())) {
			out.println("Error: No previous assessment found.");
			System.out.println("No previous assessment found at " + configFilepath);
			launchConfiguration(si);
		}
		else {
			runBackgroundJob(si, true);
		}
	}
	
	private boolean authenticateUser() {
		AuthenticationDialog ad = new AuthenticationDialog(window.getShell(), this.api, this.out);
		ad.create();
		if (ad.open() != Window.OK) {
			return false;
		}
		api.saveSession(SESSION_STRING);
		return true;
	}
	
	private void launchConfiguration(SubmissionInfo si) {
		SelectionDialog sd;
		ConfigDialog cd;
		
		sd = new SelectionDialog(window.getShell(), si); 
		cd = new ConfigDialog(window.getShell(), si);
		sd.create();
		if (sd.open() != Window.OK) {
			// TODO Handle error - I don't think this is actually an error
		}
		else {
			cd.create();
			System.out.println("Made it to config dialog");
			if (cd.open() != Window.OK) {
				// TODO Handle error
			}
			else {
				configFilepath = si.getProjectPath() + Path.SEPARATOR + CONFIG_FILENAME;
				FileSerializer.serializeSubmissionInfo(configFilepath, si);
				runBackgroundJob(si, false);
			}
			out.println("Status: Plugin completed executing");
		}
	}
	
	public void launch(String configPath) {
		initializeSwampApi();
		
		out = initializeConsole("SWAMP Plugin");
		
		try {
			if (!api.restoreSession(SESSION_STRING)) {
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
		// TODO we can fail here, i.e. by not connecting and we're not handling it as of now
		SubmissionInfo si = new SubmissionInfo(this.api);
		if (configPath == null || configPath.equals("")) {
			configFilepath = null;
		}
		else {
			configFilepath = configPath + File.separator + CONFIG_FILENAME;
		}
		if ((configFilepath != null) && (new File(configFilepath).exists())) {
			boolean returnCode = FileSerializer.deserializeSubmissionInfo(configFilepath, si);
		}
		launchConfiguration(si);
	}
	
	public boolean loggedIntoSwamp() {
		if (!initializeSwampApi()) {
			return false;
		}
		try {
			if (!api.restoreSession(SESSION_STRING)) {
				return false;
			}
		} catch (SessionExpiredException e) {
			return false;
		}
		return true;
	}
	
	public void logIntoSwamp() {
		// TODO Fill this in
	}
	
	public void logOutOfSwamp() {
		// TODO Fill this in
	}
	
	private void submitAssessment(String pkgUUID, String toolUUID, String prjUUID, String pltUUID) {
		// Submit assessment
		System.out.println("Package UUID: " + pkgUUID);
		System.out.println("Tool UUID: " + toolUUID);
		System.out.println("Project UUID: " + prjUUID);
		System.out.println("Platform UUID: " + pltUUID);
		
		api.printAllPackages(prjUUID, true);
		
		String toolName;
		try {
			toolName = api.getTool(toolUUID, prjUUID).getName();
		} catch (InvalidIdentifierException e1) {
			toolName = "Invalid tool";
		}
		PackageVersion pkg = api.getPackage(pkgUUID, prjUUID);
		assert(pkg != null);
		PackageThing pkgThing = pkg.getPackageThing();
		assert (pkgThing != null);
		String pkgName = pkgThing.getName();
		String platformName = api.getPlatform(pltUUID).getName();

		String assessUUID = null;
		try {
			assessUUID = api.runAssessment(pkgUUID, toolUUID, prjUUID, pltUUID);
		} catch (InvalidIdentifierException | IncompatibleAssessmentTupleException e) {
			// This means that some UUID was invalid
			// This really should never happen
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (assessUUID == null) {
			out.println("Error: There was an error in uploading assessment for package {" + pkgName + "} with tool {" + toolName + "} on platform {" + platformName + "}");
			// TODO handle error here
			System.err.println("Error in running assessment.");
		}
		else {
			out.println("Status: Successfully submitted assessment with tool {" + toolName + "} on platform {" + platformName +"}");
		}
	}
	
}