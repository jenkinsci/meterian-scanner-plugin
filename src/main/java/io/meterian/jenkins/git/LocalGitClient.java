package io.meterian.jenkins.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalGitClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitClient.class);

    private static final String NO_CHANGES_FOUND_WARNING = "No changes found, no branch to push to the remote repo";
    private static final String LOCAL_BRANCH_ALREADY_EXISTS_WARNING = "[meterian] Warning: %s already exists in the local repo, skipping the local branch creation process";
    private static final String REMOTE_BRANCH_ALREADY_EXISTS_WARNING = "[meterian] Warning: %s already exists in the remote repo, skipping the remote branch creation process";

    private final Git git;
    private PrintStream jenkinsLogger;
    private String branchName;

    public static void main(String[] args) throws IOException, GitAPIException {
        String pathToRepo = "/path/to/meterian/jenkins-plugin/work/workspace/TestMeterianPlugin-Freestyle-autofix";

        PrintStream noOpStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // NO-OP
            }
        });

        LocalGitClient localGitClient = new LocalGitClient(pathToRepo, noOpStream);
        if (localGitClient.hasChanges()) {
            String branchName = "fixed-by-meterian-" + localGitClient.currentBranchSHA();
            if (localGitClient.localBranchDoesNotExists(branchName)) {
                System.out.println(localGitClient.createBranch(branchName));

                Set<String> unCommittedFiles = localGitClient.listOfChanges();
                System.out.println(unCommittedFiles);

                System.out.println(localGitClient.addChangedFileToBranch(unCommittedFiles));
                System.out.println(localGitClient.commitChanges(
                        "Meterian.com",
                        "Meterian.com",
                        "info@meterian.com",
                        "Fixes applied via meterian")
                );
                localGitClient.pushBranchToRemoteRepo();
            }
        }
    }

    private Set<String> listOfChanges() throws GitAPIException {
        return git.status()
                .call()
                .getModified();
    }

    private boolean hasChanges() throws GitAPIException {
        return !git.status()
                .call()
                .isClean();
    }

    public LocalGitClient(String pathToRepo, PrintStream jenkinsLogger) throws IOException {
        this.jenkinsLogger = jenkinsLogger;

        git = Git.open(
                new File(pathToRepo)
        );
        git.checkout();
    }

    public String getRepositoryName() throws GitAPIException {
        List<RemoteConfig> remoteConfigList = git.remoteList().call();
        if ((remoteConfigList != null) && (remoteConfigList.size() > 0)) {
            String rawPath = remoteConfigList.get(0).getURIs().get(0).getRawPath();
            return rawPath.replace(".git", "");
        }
        return "";
    }

    private String currentBranchSHA() throws GitAPIException {
        List<Ref> refs = git.branchList().call();
        if (refs.size() > 0) {
            String sha = refs
                    .get(0)
                    .getObjectId()
                    .getName();
            return sha.substring(0, 5);
        }
        return "NO-SHA-FOUND";
    }

    public boolean applyCommits() throws GitAPIException {
        if (hasChanges()) {
            branchName = "fixed-by-meterian-" + currentBranchSHA();
            if (localBranchDoesNotExists(branchName)) {
                log.info(branchName + " does not exist in local or remote repos");

                createBranch(branchName);

                Set<String> unCommittedFiles = listOfChanges();
                log.info("Files changed: " + Arrays.toString(unCommittedFiles.toArray()));
                addChangedFileToBranch(unCommittedFiles);

                // TODO: need correct info for these fields
                log.info("Applying commits");
                commitChanges("Meterian.com",
                        "Meterian.com",
                        "info@meterian.com",
                        "Fixes applied via meterian");

                log.info("Finished committing changes to branch " + branchName);
            } else {
                String branchAlreadyExistsWarning = String.format(LOCAL_BRANCH_ALREADY_EXISTS_WARNING, branchName);
                log.warn(branchAlreadyExistsWarning);
                jenkinsLogger.println(branchAlreadyExistsWarning);
            }

            pushBranchToRemoteRepo();
            return true;
        } else {
            log.warn(NO_CHANGES_FOUND_WARNING);
            jenkinsLogger.println(NO_CHANGES_FOUND_WARNING);
        }
        return false;
    }

    private boolean remoteBranchDoesNotExists() throws GitAPIException {
        List<Ref> branchRefList = git.branchList()
                .call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> branch.getName().contains(branchName))
                .collect(Collectors.toList());
        return foundBranches.size() == 0;
    }

    private boolean localBranchDoesNotExists(String branchName) throws GitAPIException {
        List<Ref> branchRefList = git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> !branch.getName().contains("remotes"))
                .filter(branch -> branch.getName().contains(branchName))
                .collect(Collectors.toList());
        return foundBranches.size() == 0;
    }

    public String getOrgOrUsername() throws GitAPIException {
        String[] fullRepoName = getRepositoryName().split("/");
        if ((fullRepoName != null) && (fullRepoName.length > 0)) {
            return fullRepoName[0];
        }

        return "";
    }

    public String getBranchName() {
        return branchName;
    }

    private Ref createBranch(String branchName) throws GitAPIException {
        log.info("Creating branch");
        Ref branchCreateRef = git.branchCreate()
                .setName(branchName)
                .call();
        Ref checkoutRef = null;
        if (branchCreateRef != null) {
            checkoutRef = git.checkout()
                    .setName(branchName)
                    .call();
        }

        log.info("Created branch " + branchName);
        return checkoutRef;
    }

    private DirCache addChangedFileToBranch(Set<String> fileNames) throws GitAPIException {
        log.info("Adding files to branch: " + fileNames);

        DirCache result = null;
        for (String eachFile : fileNames) {
            result = git.add()
                    .addFilepattern(eachFile)
                    .call();
        }

        return result;
    }

    private RevCommit commitChanges(String authorName,
                                    String committerName,
                                    String email,
                                    String commitMessage) throws GitAPIException {
        log.info("Committing changes from author: " + authorName);
        return git
                .commit()
                .setAuthor(authorName, email)
                .setCommitter(committerName, email)
                .setMessage(commitMessage)
                .call();
    }

    private void pushBranchToRemoteRepo() throws GitAPIException {
        log.info("Checking if " + branchName + " already exists in remote repo");
        if (remoteBranchDoesNotExists()) {
            log.info("Branch does not exist in remote repo, started pushing branch");
            git.push().call();
            log.info("Finished pushing branch to remote repo");
        } else {
            String branchAlreadyExistsWarning = String.format(REMOTE_BRANCH_ALREADY_EXISTS_WARNING, branchName);
            log.warn(branchAlreadyExistsWarning);
            jenkinsLogger.println(branchAlreadyExistsWarning);
        }
    }
}
