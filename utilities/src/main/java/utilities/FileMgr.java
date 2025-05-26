package utilities;


import org.apache.commons.io.FileSystemUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FileMgr {

    //Logger instance for this class
    private static final Logger LOGGER = LogManager.getLogger(FileMgr.class);
    private static final int DIR_DELETION_RETRY = 10;

    /**
     * Returns all files which name starts with startFilterStr and ends with endFilterStr in the given directory
     * @param dirPath
     * @param startFilterStr
     * @param endFilterStr
     * @return
     */
    public File[] getFilteredFileList(String dirPath, String startFilterStr, String endFilterStr) {

        File dir = new File(dirPath);
        final String startFilter = startFilterStr;
        final String endFilter = endFilterStr;
        File[] fileList = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(startFilter) && name.endsWith(endFilter);
            }
        });
        return fileList;
    }

    public boolean validateInstallerFileName(String fileName, String startFilter, String endFilter) {
        if (fileName.startsWith(startFilter) && fileName.endsWith(endFilter))
            return true;
        return false;
    }

    /**
     * copy server.xml from backup directory to install directory
     * @param sourceFile
     * @param destFile
     * @return
     */
    protected boolean copyTomcatFile(String sourceFile, String destFile) {

        try {
            FileUtils.copyFile(new File(sourceFile), new File(destFile));
            return true;
        }
        catch (IOException e) {
            LOGGER.error("IOException caught during restoring " + sourceFile + " file. " + e);
        }
        catch (Exception e) {
            LOGGER.error("Exception caught during restoring " + sourceFile + " file. " + e);
        }
        return false;
    }

    /**
     * copy ROOOT Directory from backup Tomcat webapps to the installed Tomcat webapps
     * @param sourceDir
     * @param destDir
     * @return
     */
    protected  boolean copyROOTDirectory(String sourceDir, String destDir) {
        try {
            FileUtils.copyDirectory(new File(sourceDir), new File(destDir), true);
            return true;
        }
        catch (IOException e) {
            LOGGER.error("IOException caught during restoring ROOT Directory. " + e);
        }
        catch(Exception e) {
            LOGGER.error("Exception caught during restoring ROOT Directory " + e);
        }
        return false;
    }

    /**
     * clean up the directory after upgrade
     * @param dirPathToCleanup
     * @return
     */
    protected boolean cleanupDirecotry(String dirPathToCleanup) {

        try {
            FileUtils.deleteDirectory(new File(dirPathToCleanup));
            return true;
        }
        catch (IOException e) {
            LOGGER.debug("IOException caught when trying to delete " + dirPathToCleanup + e);
        }
        catch (Exception e) {
            LOGGER.debug("Exception caught when trying to delete " + dirPathToCleanup + e);
        }
        return false;

    }

    /**
     * Retry mechanism to clean up directory.
     * @param directoryToDelete
     * @return
     */
    protected boolean cleanupDirectoryWithRetry(String directoryToDelete) {

        try {
            for (int i=0; i < DIR_DELETION_RETRY; i++ ) {
                if( cleanupDirecotry(directoryToDelete)) {
                    LOGGER.debug("Successfully deleted  " + directoryToDelete + " after " + (i+1) + " try!");
                    Thread.sleep(5000);
                   return true;
                }
                Thread.sleep(5000);
            }
        }
        catch(InterruptedException e) {
            LOGGER.error("cleanupDirectoryWithRetry: InterruptedException caught " + directoryToDelete + e);
        }
        return false;
    }

    /**
     * make backup directory for the current tomcat
     * @return
     */
    protected boolean createTomcatBackupDir(String backupDir) {
        try {
            FileUtils.forceMkdir(new File(backupDir));
            return true;
        }
        catch(IOException e) {
            LOGGER.error("IOException caught when trying to create Tomcat backup directory. " + e);
        }
        return false;
    }

    /**
     * back up or restore Tomcat
     * @param sourceDir
     * @param destDir
     * @return
     */
    protected boolean backupOrRestoreTomcat(String sourceDir, String destDir, boolean isRestore){

        String logStr = "restore";
        if (!isRestore)
            logStr = "backup";

        FileMgr.TomcatBackupOrRestoreProgress progress = new FileMgr.TomcatBackupOrRestoreProgress(sourceDir, destDir);
        progress.start();

        try {
            FileUtils.copyDirectory(new File(sourceDir), new File(destDir), true);
            progress.setCheckProgress(false);
            return true;
        }
        catch (IOException e) {
            LOGGER.error("backupOrRestoreTomcat: IOException caught during " + logStr + e);
            progress.setCheckProgress(false);
        }
        catch(Exception e) {
            LOGGER.error("backupOrRestoreTomcat: Exception caught during " + logStr + e);
            progress.setCheckProgress(false);
        }
        return false;
    }


    /**
     * validate input to make sure it is an existing directory
     * @param newInstallerPath
     * @return
     */
    protected boolean validateInstallerPath(String newInstallerPath) {

        boolean bResults = true;
        try {
            File validateFile = new File(newInstallerPath);
            if (!validateFile.exists() || !validateFile.isFile())
                bResults =  false;
        }catch (Exception e) {
            LOGGER.error("validateInstallerPath: Exception caught! " + e);
            bResults = false;
        }

        if (!bResults) {
            LOGGER.info("The file path " + newInstallerPath + " does not exist!");
            LOGGER.info("Please enter the full path name for the new Tomcat installer.");
            return false;
        }
        return true;
    }

    /**
     * Check disk space is available for backup
     * @param drivePathToCheck
     * @param tomcatDir
     * @return
     */
    protected boolean checkDiskSpace(String drivePathToCheck, String tomcatDir) {

        try {
            long driveFreeSpace = getDriveFreeSpace(drivePathToCheck);
            long tomcatDirSize = getDirectorySize(tomcatDir);
            LOGGER.debug("driveFreeSpace=" + driveFreeSpace * 1024);  //in byte
            LOGGER.debug("tomcatDirSize =" + tomcatDirSize);   //in byte
            if (driveFreeSpace * 1024 > tomcatDirSize)  {
                return true;
            }
        }
        catch(Exception e) {
            LOGGER.error("Exception caught during space check." + e);
        }
        return false;
    }


    /**
     * Save the backup tomcat version to the file under back up directory
     * @param backupTomcatVersion
     * @param versionFilePath
     * @return
     */
    protected boolean writeBackupTomcatVersion(String backupTomcatVersion,String versionFilePath) {

        try {
            FileUtils.writeStringToFile(new File(versionFilePath), backupTomcatVersion);
            return true;
        }
        catch (IOException e) {
            LOGGER.error("IOException caught during writing Tomcat Version to file:" + versionFilePath + e);
        }
        return false;
    }

    //return value in byte
    protected long getDirectorySize(String path) {
        return FileUtils.sizeOfDirectory(new File(path));
    }

    protected  long getDirectorySize(File dirPath) {
        return FileUtils.sizeOfDirectory(dirPath);
    }

    //return value in kb
    protected long getDriveFreeSpace(String drivePath) throws IOException{
        return FileSystemUtils.freeSpaceKb(drivePath);
    }

    /**
     * check the backup progress
     * @param destDirectory
     * @param sourceDirSize
     * @return
     */
    protected int checkBackupOrRestoreProgress(File destDirectory, long sourceDirSize) {
        long copied = getDirectorySize(destDirectory);
        float percentageDone = 0;
        if (copied > 0) {
            percentageDone = (float)((double)(copied*100)/(double)sourceDirSize);
            LOGGER.info((int)percentageDone + "% done");
        }
        return (int)percentageDone;
    }

    /**
     * get ACL Entries list
     * @param path
     * @return
     */
    protected List<AclEntry> getACLEntriesList(String path) {

        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(Paths.get(path),
                    AclFileAttributeView.class);

            if (aclView != null) {
                return aclView.getAcl();
            }
        } catch (IOException e) {
            LOGGER.error("IOException caught during getACLEntriesList on:" + path + e);
        } catch (InvalidPathException e) {
            LOGGER.error("InvalidPathException caught during getACLEntriesList on:" + path + e);
        } catch (Exception e) {
            LOGGER.error("Exception caught during getACLEntriesList on:" + path + e);
        }

        return Collections.emptyList();
    }

    /**
     * get ACL Entries of first file under path
     * @param path
     * @return
     */
    
    
    protected List<AclEntry> getACLEntriesListofFirstFile(String path) {
        if (StringUtils.isNotBlank(path)) {
            try {
                Optional<Path> file = Files.walk(Paths.get(path)).filter(Files::isRegularFile).findFirst();
                if (file.isPresent()) {
                    return getACLEntriesList(file.get().toString());
                }
            } catch (IOException e) {
                LOGGER.error("IOException caught during getACLEntriesList on:" + path + e);
            } catch (InvalidPathException e) {
                LOGGER.error("InvalidPathException caught during getACLEntriesList on:" + path + e);
            }
        }
        return Collections.emptyList();
    }


    /**
     * Apply ACL entries recursively on given file type under given dir
     * @param dir
     * @param fileType
     * @param aclEntries
     * @return
     */
    
    
    protected boolean applyACLEntries(String dir, Predicate<Path> fileType, List<AclEntry> aclEntries) {

        if (StringUtils.isBlank(dir) || aclEntries.isEmpty())
            return false;

        try {
            Stream<Path> paths = Files.walk(Paths.get(dir));
            paths.filter(fileType).forEach(path -> {
                AclFileAttributeView aclView = Files.getFileAttributeView(path,
                        AclFileAttributeView.class);
                if (aclView == null)
                    return;
                try {
                    aclView.setAcl(aclEntries);
                } catch (IOException e) {
                    LOGGER.error("IOException caught during setACLEntry on:" + path.toString() + e);
                    throw new RuntimeException(e);
                }
            });

            return true;

        } catch (Exception e) {
            LOGGER.error("IOException caught during applyACLEntries on:" + dir + e);
        }
        return false;
    }


    /**
     * Unzips a zip file to the provided unzip location.
     * We use icm\bin\Unzip.class for the actual unzip.
     * @param zipFile
     * @param unzipLocation
     * @return
     */
    protected boolean unzipFile(String zipFile, String unzipLocation) {
        LOGGER.info("Unzipping '" + zipFile + "' to '" + unzipLocation + "'");
        Map<String, String> env = System.getenv();
        String cceJavaHome = env.get("CCE_JAVA_HOME");
        if (cceJavaHome == null){
            LOGGER.error("Unable to retrieve the CCE_JAVA_HOME. Unzip failed. Please attempt to unzip manually.");
            return false;
        }
        String javaExecutable = cceJavaHome + "\\bin\\java";
        String unzipClassLocation = RegistryManager.getIcmInstallDrive() + ":\\icm\\bin";

        String unzipCommand = javaExecutable + " -cp " + unzipClassLocation + " Unzip " + zipFile + " " + unzipLocation;
        LOGGER.info("Unzip command :" + unzipCommand);

        try {
            Process proc = Runtime.getRuntime().exec(unzipCommand);
            int retCode = proc.waitFor();
            if (retCode != 0) {
                LOGGER.error("Error occurred during unzip:" + retCode);
                return false;
            }
            Thread.sleep(5000);
            return true;
        } catch (InterruptedException e) {
            // That's okay
            return true;
        } catch (Exception e) {
            LOGGER.error("An exception was emitted during unzip - " + e.getMessage() , e);
            return false;
        }
    }

    /**
     * track the progress for copying directory used by both backup and restore
     */
    protected class TomcatBackupOrRestoreProgress extends Thread{

        String sourceDir;
        String destDir;
        boolean checkProgress;

        synchronized public boolean isCheckProgress() {
            return checkProgress;
        }

        synchronized public void setCheckProgress(boolean checkProgress) {
            this.checkProgress = checkProgress;
        }

        public TomcatBackupOrRestoreProgress(String sourceDir, String destDir) {
            super();
            this.sourceDir = sourceDir;
            this.destDir = destDir;
            checkProgress = true;
        }

        public void run() {
            try {
                // File tomcatInstallDirectory = new File(tomcatInstallDir);
                long sourceDirSize = getDirectorySize(sourceDir);
                File destDirectory = new File(destDir);
                while (isCheckProgress()) {
                    checkBackupOrRestoreProgress(destDirectory, sourceDirSize);
                    Thread.sleep(10000); //10 seconds to check
                }
            }
            catch (InterruptedException e) {
                LOGGER.error("InterruptedException occurred when checking the progress for Tomcat backup.");
            }
            catch (Exception e) {
                LOGGER.error("Exception occurred when checking the progress for Tomcat backup.");
            }
        }
    }
}