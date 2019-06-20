package io.meterian.jenkins.autofixfeature.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

public class LocalGitClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitClient.class);

    public static final String NO_CHANGES_FOUND_WARNING = "No changes found (no fixes generated), no branch to push to the remote repo.";

    private static final String REMOTE_BRANCH_ALREADY_EXISTS_WARNING = "[meterian] Warning: %s already exists in the remote repo, skipping the remote branch creation process.";
    private static final String FIXED_BY_METERIAN = "fixed-by-meterian";

    private final Git git;
    private final String meterianGithubUser;  // Machine User name
    private final String meterianGithubEmail; // Email associated with the Machine User
    private PrintStream jenkinsLogger;
    private String currentBranch;

    public LocalGitClient(String pathToRepo,
                          String meterianGithubUser,
                          String meterianGithubEmail,
                          PrintStream jenkinsLogger) {
        this.meterianGithubUser = meterianGithubUser;
        this.meterianGithubEmail = meterianGithubEmail;

        this.jenkinsLogger = jenkinsLogger;

        log.info(String.format("Workspace (path to the git repo): %s", pathToRepo));
        try {
            git = Git.open(new File(pathToRepo));
            currentBranch = getCurrentBranch();
            log.info(String.format("Workspace is pointing to branch %s (%s)", currentBranch, getCurrentBranchSHA()));
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

    public void applyCommitsToLocalRepo() throws GitAPIException, IOException {
            createBranch();

            Set<String> unCommittedFiles = listOfChanges();
            log.info(String.format("Files changed: %s", Arrays.toString(unCommittedFiles.toArray())));
            addChangedFileToBranch(unCommittedFiles);

            log.info("Applying commits");
            commitChanges(meterianGithubUser,
                    meterianGithubUser,
                    meterianGithubEmail,
                    getMeterianCommitMessage());

            log.info(String.format("Finished committing changes to branch %s", currentBranch));
    }

    private String getMeterianCommitMessage() {
        return String.format("Fixes applied via %s", meterianGithubUser);
    }

    public String getOrgOrUsername() throws GitAPIException {
        String[] fullRepoName = getRepositoryName().split("/");
        if (fullRepoName.length > 0) {
            return fullRepoName[0].isEmpty() ? fullRepoName[1] : fullRepoName[0];
        }

        return "";
    }

    public Ref checkoutBranch(String branch) throws GitAPIException {
        return git.checkout()
                .setName(branch)
                .call();
    }

    public void pushBranchToRemoteRepo() {
        try {
            log.debug("Checking if current branch was created by Meterian");
            if (currentBranchWasCreatedByMeterianClient()) {
                log.info(String.format("Checking if the branch %s to be created already exists in remote repo", currentBranch));
                git.fetch()
                        .setRemoveDeletedRefs(true)
                        .call();
                if (meterianRemoteBranchDoesNotExists()) {
                    log.info(String.format("Branch %s does not exist in remote repo, started pushing branch", currentBranch));
                    git.push().call();
                    log.info("Finished pushing branch to remote repo");
                } else {
                    String branchAlreadyExistsWarning = String.format(REMOTE_BRANCH_ALREADY_EXISTS_WARNING, currentBranch);
                    log.warn(branchAlreadyExistsWarning);
                    jenkinsLogger.println(branchAlreadyExistsWarning);
                }
            } else {
                log.debug("Current branch was not created by Meterian");
            }
        } catch (Exception ex) {
            String couldNotPushDueToError = String.format("Could not push branch %s to remote repo due to error: %s", currentBranch, ex.getMessage());
            log.debug(couldNotPushDueToError);
            jenkinsLogger.println(couldNotPushDueToError);

            throw new RuntimeException(ex);
        }
    }

    public String getCurrentBranch() throws IOException, GitAPIException {
        if ((currentBranch != null) && (!currentBranch.isEmpty())) {
            return currentBranch;
        }

        currentBranch = stripOffRefsPrefix(
                Objects.requireNonNull(getHeadRef()).getName()
        );

        if (currentBranch.equals("HEAD")) {
            List<Ref> refs = git.branchList()
                    .setContains("HEAD")
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call();
            refs = refs.stream()
                    .filter(ref -> !ref.getName().contains("HEAD"))
                    .collect(Collectors.toList());

            currentBranch = refs.size() < 1
                    ? "master"
                    : refs.get(refs.size() - 1).getName();

            currentBranch = stripOffRefsPrefix(currentBranch);
        }

        return currentBranch;
    }

    public boolean currentBranchWasCreatedByMeterianClient() throws GitAPIException {
        Iterable<RevCommit> logs = git.log().call();
        Iterator<RevCommit> iterator = logs.iterator();
        if (iterator.hasNext()) {
            RevCommit currentCommit = iterator.next();
            PersonIdent author = currentCommit.getAuthorIdent();
            return author.getName().equalsIgnoreCase(meterianGithubUser) &&
                    author.getEmailAddress().equalsIgnoreCase(meterianGithubEmail);
        }
        return false;
    }

    private String getMeterianBranchName() throws GitAPIException, IOException {
        if (currentBranchWasCreatedByMeterianClient()) {
            return getCurrentBranch();
        }
        return meterianBranchName(getCurrentBranchSHA());
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

    public boolean hasChanges() throws GitAPIException {
        return !git.status()
                .call()
                .isClean();
    }

    public void resetChanges() throws GitAPIException {
        if (hasChanges()) {
            git.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .call();
        }
    }

    public boolean currentBranchHasNotBeenFixedYet() throws GitAPIException {
        return getFixedBranchNameForCurrentBranch().isEmpty();
    }

    public String getFixedBranchNameForCurrentBranch() throws GitAPIException {
        List<Ref> branchRefList = git.branchList().call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> !branch.getName().contains("remotes"))
                .filter(this::byLocalFixedBranchName)
                .collect(Collectors.toList());
        return foundBranches.size() == 0 ? "" : foundBranches.get(0).getName();
    }

    public List<Ref> findBranchByName(String branchName) throws GitAPIException {
        return git.branchList()
                .setContains(branchName)
                .call();
    }

    private String stripOffRefsPrefix(String currentBranch) {
        return currentBranch
                .replace("refs/heads/", "")
                .replace("refs/remotes/", "")
                .replace("origin/", "");
    }

    private String getCurrentBranchSHA() throws IOException {
        String longSha = Objects.requireNonNull(getHeadRef())
                .getObjectId()
                .getName();

        return longSha.isEmpty()
                ? "NO-SHA-FOUND"
                : shortenSha(longSha); // Extract short SHA from the long 40-chars SHA string
    }

    private Ref getHeadRef() throws IOException {
        return git.getRepository()
                .findRef("HEAD")
                .getTarget();
    }

    private boolean meterianRemoteBranchDoesNotExists() throws GitAPIException {
        List<Ref> branchRefList = git.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> branch.getName().contains("remotes"))
                .filter(branch -> branch.getName().contains(currentBranch))
                .collect(Collectors.toList());
        return foundBranches.size() == 0;
    }

    private Ref createBranch() throws GitAPIException, IOException {
        log.info("Creating branch");
        currentBranch = getMeterianBranchName();
        Ref branchCreateRef = git.branchCreate()
                .setName(currentBranch)
                .call();
        Ref checkoutRef = null;
        if (branchCreateRef != null) {
            checkoutRef = checkoutBranch(currentBranch);
        }

        log.info(String.format("Created branch %s and switched to it", currentBranch));
        return checkoutRef;
    }

    private DirCache addChangedFileToBranch(Set<String> fileNames) throws GitAPIException {
        log.info(String.format("Adding files to branch: %s", fileNames));

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
        log.info(String.format("Committing changes from author: %s", authorName));
        return git
                .commit()
                .setAuthor(authorName, email)
                .setCommitter(committerName, email)
                .setMessage(commitMessage)
                .call();
    }

    private boolean byLocalFixedBranchName(Ref branch) {
        try {
            return branch.getName().contains(
                    String.format("%s-%s", FIXED_BY_METERIAN, getCurrentBranchSHA())
            );
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
