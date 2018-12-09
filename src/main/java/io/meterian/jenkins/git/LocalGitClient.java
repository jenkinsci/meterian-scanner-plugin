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
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalGitClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitClient.class);

    private static final String NO_CHANGES_FOUND_WARNING = "No changes found, no branch to push to the remote repo";
    private static final String REMOTE_BRANCH_ALREADY_EXISTS_WARNING = "[meterian] Warning: %s already exists in the remote repo, skipping the remote branch creation process";
    private static final String FIXED_BY_METERIAN = "fixed-by-meterian";

    private final Git git;
    private PrintStream jenkinsLogger;
    private String branchName;

    public static void main(String[] args) throws GitAPIException {
        String pathToRepo = "/path/to/meterian/jenkins-plugin/work/workspace/TestMeterianPlugin-Freestyle-autofix";

        PrintStream noOpStream = new PrintStream(new OutputStream() {
            public void write(int b) {
                // NO-OP
            }
        });

        LocalGitClient localGitClient = new LocalGitClient(pathToRepo, noOpStream);

        if (localGitClient.hasChanges()) {
            if (localGitClient.localBranchDoesNotExists()) {
                System.out.println(localGitClient.createBranch());

                Set<String> unCommittedFiles = localGitClient.listOfChanges();
                System.out.println(unCommittedFiles);

                System.out.println(localGitClient.addChangedFileToBranch(unCommittedFiles));
                System.out.println(localGitClient.commitChanges(
                        "meterian-bot",
                        "meterian-bot",
                        "bot.github@meterian.io",
                        "Fixes applied via meterian-bot")
                );
            }
        }
        localGitClient.pushBranchToRemoteRepo();
    }

    public LocalGitClient(String pathToRepo, PrintStream jenkinsLogger) {
        this.jenkinsLogger = jenkinsLogger;

        try {
            git = Git.open(new File(pathToRepo));
            branchName = getMeterianBranchName();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getRepositoryName() throws GitAPIException {
        List<RemoteConfig> remoteConfigList = git.remoteList().call();
        if ((remoteConfigList != null) && (remoteConfigList.size() > 0)) {
            String rawPath = remoteConfigList.get(0).getURIs().get(0).getRawPath();
            return rawPath.replace(".git", "");
        }
        return "";
    }

    public void applyCommitsToLocalRepo() throws GitAPIException {
        if (hasChanges()) {
            createBranch();

            Set<String> unCommittedFiles = listOfChanges();
            log.info("Files changed: " + Arrays.toString(unCommittedFiles.toArray()));
            addChangedFileToBranch(unCommittedFiles);

            log.info("Applying commits");
            commitChanges("meterian-bot",
                    "meterian-bot",
                    "bot.github@meterian.io",
                    "Fixes applied via meterian-bot");

            log.info("Finished committing changes to branch " + branchName);
        } else {
            log.warn(NO_CHANGES_FOUND_WARNING);
            jenkinsLogger.println(NO_CHANGES_FOUND_WARNING);
        }
    }

    public String getBranchName() {
        return branchName;
    }

    public boolean localBranchDoesNotExists() throws GitAPIException {
        List<Ref> branchRefList = git.branchList()
                .call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> !branch.getName().contains("remotes"))
                .filter(branch -> branch.getName().contains(FIXED_BY_METERIAN + "-"))
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

    public void pushBranchToRemoteRepo() throws GitAPIException {
        log.info("Checking if " + branchName + " already exists in remote repo");
        git.fetch()
                .setRemoveDeletedRefs(true)
                .call();
        git.checkout()
                .setName(branchName)
                .call();
        if (remoteBranchDoesNotExists()) {
            log.info("Branch does not exist in remote repo, started pushing branch");
            git.push()
                    .call();
            log.info("Finished pushing branch to remote repo");
        } else {
            String branchAlreadyExistsWarning = String.format(REMOTE_BRANCH_ALREADY_EXISTS_WARNING, branchName);
            log.warn(branchAlreadyExistsWarning);
            jenkinsLogger.println(branchAlreadyExistsWarning);
        }
    }

    private String getMeterianBranchName() throws GitAPIException {
        if (checkIfTheCurrentBranchWasCreatedByMeterianClient()) {
            return currentBranchName();
        }
        return meterianBranchName(currentBranchSHA());
    }

    private boolean checkIfTheCurrentBranchWasCreatedByMeterianClient() throws GitAPIException {
        Iterable<RevCommit> logs = git.log().call();
        if (logs.iterator().hasNext()) {
            RevCommit eachCommit = logs.iterator().next();
            String suffix = shortenSha(eachCommit.toString().split(" ")[1]);
            return currentBranchName().equals(meterianBranchName(suffix));
        }
        return false;
    }

    private String shortenSha(String value) {
        return value.substring(0, 7);
    }

    private String meterianBranchName(String suffix) {
        return String.format("%s-%s", FIXED_BY_METERIAN, suffix);
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

    private Ref currentBranchRef() throws GitAPIException {
        List<Ref> refs = git.branchList().call();
        if (refs.size() > 0) {
            return refs.get(0);
        }
        return null;
    }

    private String currentBranchName() throws GitAPIException {
        return currentBranchRef() != null
                ? Objects.requireNonNull(currentBranchRef()).getName()
                : "";
    }

    private String currentBranchSHA() throws GitAPIException {
        String longSha = Objects.requireNonNull(currentBranchRef())
                .getObjectId()
                .getName();

        return longSha.isEmpty()
                ? "NO-SHA-FOUND"
                : shortenSha(longSha); // Extract short SHA from the long 40-chars SHA string
    }

    private boolean remoteBranchDoesNotExists() throws GitAPIException {
        List<Ref> branchRefList = git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> branch.getName().contains("remotes"))
                .filter(branch -> branch.getName().contains(branchName))
                .collect(Collectors.toList());
        return foundBranches.size() == 0;
    }

    private Ref createBranch() throws GitAPIException {
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

}
