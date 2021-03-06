package models;

import com.google.gson.Gson;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class Utils {

    public static void cleanWorkingDirectory() throws IOException {
        File dir = new File(String.valueOf(Utils.seekRepoRootFolder()));
        for(File file: dir.listFiles()) {
            if (file.isDirectory() && !file.getName().equals(".minigit")) {
                deleteDirectory(file);
            } else if (!file.getName().equals(".minigit")) {
                Files.delete(file.toPath());
            }
        }
    }

    public static Repository readRepository() throws IOException {
        Path pathToRepoFile = Paths.get(seekMinigitFolder().toString(),findCurrentBranchJsonFileName()).normalize();
        return readGivenBranch(pathToRepoFile);
    }

    public static Repository readBranch(String branchName) throws IOException {
        Path pathToRepoFile = Paths.get(seekMinigitFolder().toString(), "branches", branchName + ".json").normalize();
        return readGivenBranch(pathToRepoFile);
    }

    private static Repository readGivenBranch(Path pathToRepoFile) throws IOException {
        File file = new File(pathToRepoFile.toAbsolutePath().toString());
        Gson gson = new Gson();

        try {
            Repository repo = gson.fromJson(new FileReader(file, Charset.forName("UTF-8")), Repository.class);
            return repo;
        } catch(IOException e) {
            System.out.println("Repository has not been initialized. Try: init.");
            throw new RuntimeException(e);
        }
    }

    public static void initializeRepoInCurrentFolder(Repository repository) throws IOException {
        Path pathToRepoFile = Paths.get(".minigit","master.json").normalize();

        if(!Files.exists(pathToRepoFile)) {
            createFolder(Constants.MINIGIT_DIRECTORY_NAME);
        }

        createRepositoryJsonFile(repository, pathToRepoFile);
    }

    public static void createRepositoryJsonFile(Repository repository, Path pathToRepoFile) throws IOException {
        File file = new File(pathToRepoFile.toAbsolutePath().toString());
        Gson gson = new Gson();

        try (FileWriter writer = new FileWriter(file, Charset.forName("UTF-8"))) {
            gson.toJson(repository, writer);
        }
    }

    public static void saveRepository(Repository repository) throws IOException {
        // here we should:
        // serialize the repository
        // save the repository file to .minigit folder

        Path pathToRepoFile = Paths.get(seekMinigitFolder().toString(),findCurrentBranchJsonFileName()).normalize();

        if(!Files.exists(pathToRepoFile)) {
            throw new IOException("Can't save repository: repository doesn't seem initialized");
        }

        createRepositoryJsonFile(repository, pathToRepoFile);
    }

    public static void createFolder(String fileName) throws IOException {
        Path pathToRepoFolder = Paths.get(fileName);
        Files.createDirectories(pathToRepoFolder);
    }

    public static String repositoryFileSha1(String uuid) throws IOException {
        try (InputStream is = Files.newInputStream(Paths.get(seekMinigitFolder().toString(), uuid + ".zip"))) {
            return org.apache.commons.codec.digest.DigestUtils.sha1Hex(is);
        }
    }

    public static Commit getCommit(String commitHash, Repository repo) {
        for (Commit commit : repo.getCommits()) {
            if (commit.getHash().equals(commitHash)) {
                return commit;
            }
        }
        return null;
    }

    public static String getAncestorOfHash(String commitHash) throws IOException {
        Repository repo = readRepository();
        for (Commit commit : repo.getCommits()) {
            if (commit.getHash().equals(commitHash)) {
                return commit.getAncestorHash();
            }
        }
        return null;
    }

    public static void getAllFilePathsInDir(File dir, List<String> filePaths) throws IOException {
        for (File file: dir.listFiles()) {
            if (!file.isDirectory())  {

                String[] dirs = Utils.getRelativePathOfFile(file).toString().split(File.separator);

                String path = String.join(File.separator, Arrays.copyOfRange(dirs, 3, dirs.length));
                filePaths.add(path);
            } else {
                getAllFilePathsInDir(file, filePaths);
            }
        }
    }

    public static Path getRelativePathOfFile(File file) throws IOException {
        Path repoRoot = seekRepoRootFolder();
        Path filePath = Paths.get(file.getPath());
        return filePath.subpath(repoRoot.getNameCount(), filePath.getNameCount());
    }

    public static void deleteDirectory(File dir) throws IOException {
        if (!dir.exists())
            return;
        for (File file: dir.listFiles()) {
            if (!file.isDirectory())  {
                Files.delete(file.toPath());
            } else {
                deleteDirectory(file);
            }
        }
        Files.delete(dir.toPath());
    }

    public static void addFilesToZip(ZipFile zip, ZipParameters zipParameters, File[] files) throws ZipException {
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    System.out.println("Added folder: " + file.getName());
                    zip.addFolder(file, zipParameters);
                } else {
                    System.out.println("Added file: " + file.getName());
                    zip.addFile(file, zipParameters);
                }
            }
        }
    }

    public static Path seekRepoRootFolder() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"));
        while(!Files.isDirectory(path.resolve(Constants.MINIGIT_DIRECTORY_NAME))){
            path = path.getParent();
            if (path == null) {
                throw new IOException("Minigit folder not found!");
            }
        }
        return path;
    }

    public static Path seekMinigitFolder() throws IOException {
        return seekRepoRootFolder().resolve(Constants.MINIGIT_DIRECTORY_NAME);
    }

    public static void findAddedFiles(List<String> commitDirPaths, List<String> ancestorDirPaths, Map<String, Integer> files) throws IOException {
        for (String commitDirPath : commitDirPaths) {
            if(!ancestorDirPaths.contains(commitDirPath)) {
                files.put(commitDirPath, Constants.ADDED_FILE);
            } else {
                files.put(commitDirPath, Constants.CHANGED_FILE);
            }
        }
    }

    public static void findRemovedFiles(List<String> commitDirPaths, List<String> ancestorDirPaths, Map<String, Integer> files) throws IOException {
        for (String ancestorDirPath : ancestorDirPaths) {
            if (!commitDirPaths.contains(ancestorDirPath)) {
                files.put(ancestorDirPath, Constants.REMOVED_FILE);
            }
        }
    }

    public static String findCurrentBranchJsonFileName() throws IOException {
        File dir = new File(String.valueOf(seekMinigitFolder()));

        for (File file : dir.listFiles()) {
            if(file.getName().endsWith(".json"))
                return file.getName();
        }
        return null;
    }

    public static List<String> findAllBranchNames() throws IOException {
        List<String> list = new ArrayList<>();
        File branches = new File(String.valueOf(Paths.get(seekMinigitFolder().toString(), "branches")));

        if (branches.listFiles() != null) {
            for (File file : branches.listFiles()) {
                list.add(file.getName());
            }
        } else {
            return list;
        }

        return list;
    }
}
