package io.meterian.jenkins.autofixfeature.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class LocalGitClient {

    static final Logger log = LoggerFactory.getLogger(LocalGitClient.class);

    public static final String NO_CHANGES_FOUND_WARNING = "No changes found (no fixes generated), no branch to push to the remote repo.";

    public static final String HTTPS_ORIGIN = "https-origin";

    private static final String REMOTE_BRANCH_ALREADY_EXISTS_WARNING = "[meterian] Warning: %s already exists in the remote repo, skipping the remote branch creation process.";
    private static final String FIXED_BY_METERIAN = "fixed-by-meterian";

    private final String meterianGithubUser;  // Machine User name
    private final String meterianGithubEmail; // Email associated with the Machine User
    private final String pathToRepo;

    private String organisationOrUsername;
    private String repositoryName;

    private PrintStream jenkinsLogger;

    private Git git;
    private String currentBranch;

    protected CredentialsProvider credentialsProvider;

    public LocalGitClient(String pathToRepo,
                          String meterianGithubUser,
                          String meterianGithubToken,
                          String meterianGithubEmail,
                          PrintStream jenkinsLogger) {
        // See https://www.codeaffine.com/2014/12/09/jgit-authentication/ or
        // https://github.blog/2012-09-21-easier-builds-and-deployments-using-git-over-https-and-oauth/
        // for explanation on why this is allowed
        // First argument of UsernamePasswordCredentialsProvider is the token, the second argument is empty/blank
        credentialsProvider = new UsernamePasswordCredentialsProvider(
                meterianGithubToken, "");

        this.meterianGithubUser = meterianGithubUser;
        this.meterianGithubEmail = meterianGithubEmail;

        this.pathToRepo = pathToRepo;
        this.jenkinsLogger = jenkinsLogger;
        
        log.info(String.format("Workspace (path to the git repo): %s", pathToRepo));
    }

    public String getOrganisationOrUsername() {
        if ((organisationOrUsername == null) || (organisationOrUsername.isEmpty())) {
            git();
        }
        return organisationOrUsername;
    }

    public String getRepositoryName() {
        if ((repositoryName == null) || (repositoryName.isEmpty())) {
            git();
        }
        return repositoryName;
    }

    public void applyCommitsToLocalRepo() throws GitAPIException, IOException {
        Set<String> unCommittedFiles = listOfChanges();
        log.info(String.format("Files changed: %s", Arrays.toString(unCommittedFiles.toArray())));
        addChangedFileToBranch(unCommittedFiles);

        log.info("Applying commits");
        commitChanges(meterianGithubUser,
                meterianGithubUser,
                meterianGithubEmail,
                getMeterianCommitMessage());

        log.info(String.format("Finished committing changes to branch %s", getCurrentBranch()));
    }

    private String getMeterianCommitMessage() {
        return String.format("Fixes applied via %s", meterianGithubUser);
    }

    public Ref checkoutBranch(String branch) throws GitAPIException {
        return git().checkout()
                .setName(branch)
                .call();
    }

    public void pushBranchToRemoteRepo() throws IOException, GitAPIException {
        try {
            log.debug("Checking if current branch was created by Meterian");
            if (currentBranchWasCreatedByMeterianClient()) {
                log.info(String.format("Checking if the branch %s to be created already exists in remote repo", getCurrentBranch()));
                git().fetch()
                        .setRemote(HTTPS_ORIGIN)
                        .setRemoveDeletedRefs(true)
                        .call();
                if (meterianRemoteBranchDoesNotExists()) {
                    log.info(String.format("Branch %s does not exist in remote repo, started pushing branch", getCurrentBranch()));
                    git().push()
                            .setRemote(HTTPS_ORIGIN)
                            .setCredentialsProvider(credentialsProvider)
                            .call();
                    log.info("Finished pushing branch to remote repo");
                } else {
                    String branchAlreadyExistsWarning = String.format(REMOTE_BRANCH_ALREADY_EXISTS_WARNING, getCurrentBranch());
                    log.warn(branchAlreadyExistsWarning);
                    jenkinsLogger.println(branchAlreadyExistsWarning);
                }
            } else {
                log.debug("Current branch was not created by Meterian");
            }
        } catch (Exception ex) {
            String couldNotPushDueToError =
                    String.format("Could not push branch %s to remote repo due to error: %s", getCurrentBranch(), ex.getMessage());
            log.error(couldNotPushDueToError, ex);
            jenkinsLogger.println(couldNotPushDueToError);

            throw new RuntimeException(ex);
        }
    }

    public String getCurrentBranch() throws IOException, GitAPIException {
        if ((currentBranch != null) && (! currentBranch.isEmpty())) {
            return currentBranch;
        }
        
        currentBranch = stripOffRefsPrefix(
                Objects.requireNonNull(getHeadRef()).getName()
        );

        if (currentBranch.equals("HEAD")) {
            List<Ref> refs = git().branchList()
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
        Iterable<RevCommit> logs = git().log().call();
        Iterator<RevCommit> iterator = logs.iterator();
        if (iterator.hasNext()) {
            RevCommit currentCommit = iterator.next();
            PersonIdent author = currentCommit.getAuthorIdent();
            return author.getName().equalsIgnoreCase(meterianGithubUser) &&
                    author.getEmailAddress().equalsIgnoreCase(meterianGithubEmail);
        }
        return false;
    }

    public String getMeterianBranchName() throws GitAPIException, IOException {
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
        return git().status()
                .call()
                .getModified();
    }

    public boolean hasChanges() throws GitAPIException {
        return !git().status()
                .call()
                .isClean();
    }

    public void resetChanges() throws GitAPIException {
        if (hasChanges()) {
            git().reset()
                .setMode(ResetCommand.ResetType.HARD)
                .call();
        }
    }

    public boolean currentBranchHasNotBeenFixedYet() throws GitAPIException {
        return getFixedBranchNameForCurrentBranch().isEmpty();
    }

    public String getFixedBranchNameForCurrentBranch() throws GitAPIException {
        List<Ref> branchRefList = git().branchList().call();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> !branch.getName().contains("remotes"))
                .filter(this::byLocalFixedBranchName)
                .collect(Collectors.toList());
        return foundBranches.size() == 0 ? "" : foundBranches.get(0).getName();
    }

    public List<Ref> findBranchByName(String branchName) throws GitAPIException {
        return git().branchList()
                .setContains(branchName)
                .call();
    }

    private String stripOffRefsPrefix(String value) {
        return value
                .replace("refs/heads/", "")
                .replace("refs/remotes/", "")
                .replace("origin/", "");
    }

    public String getCurrentBranchSHA() throws IOException {
        String longSha = Objects.requireNonNull(getHeadRef())
                .getObjectId()
                .getName();

        return longSha.isEmpty()
                ? "NO-SHA-FOUND"
                : shortenSha(longSha); // Extract short SHA from the long 40-chars SHA string
    }

    private Ref getHeadRef() throws IOException {
        return git().getRepository()
                .findRef("HEAD")
                .getTarget();
    }

    private boolean meterianRemoteBranchDoesNotExists() throws GitAPIException, IOException {
        List<Ref> branchRefList = git().branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call();

        String targetBranch = getCurrentBranch();
        List<Ref> foundBranches = branchRefList
                .stream()
                .filter(branch -> branch.getName().contains("remotes"))
                .filter(branch -> branch.getName().contains(targetBranch))
                .collect(Collectors.toList());
        return foundBranches.size() == 0;
    }

    public Ref createBranch(String branchName) throws GitAPIException, IOException {
        log.info("Creating branch");
        Ref branchCreateRef = git().branchCreate()
                .setName(branchName)
                .call();
        Ref checkoutRef = null;
        if (branchCreateRef != null) {
            checkoutRef = checkoutBranch(branchName);
        }

        currentBranch = stripOffRefsPrefix(checkoutRef.getName());

        log.info(String.format("Created branch %s and switched to it", getCurrentBranch()));
        return checkoutRef;
    }

    public void createMeterianBranch() throws GitAPIException, IOException {
        createBranch(getMeterianBranchName());
    }

    private DirCache addChangedFileToBranch(Set<String> fileNames) throws GitAPIException {
        log.info(String.format("Adding files to branch: %s", fileNames));

        DirCache result = null;
        for (String eachFile : fileNames) {
            result = git().add()
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
        return git()
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

    protected synchronized Git git() {
        if (this.git == null)
            try {
                this.git = Git.open(new File(pathToRepo));
                log.info(String.format("Workspace is pointing to branch %s (%s)", getCurrentBranch(), getCurrentBranchSHA()));
                parseRepoURL();
                addRemoteForHttpsURI();
                enlistRemotes();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

        return git;
    }

    private void parseRepoURL() throws GitAPIException {
        List<RemoteConfig> remoteConfigList = git.remoteList().call();
        if ((remoteConfigList != null) && (remoteConfigList.size() > 0)) {
            RemoteConfig remoteOrigin = remoteConfigList
                    .stream()
                    .filter(remote -> remote.getName().equalsIgnoreCase("origin"))
                    .findAny()
                    .get();
            String rawRepoURI = remoteOrigin
                    .getURIs()
                    .get(0)
                    .getRawPath();

            if (rawRepoURI.startsWith("/")) {
                rawRepoURI = rawRepoURI.substring(1);
            }
            rawRepoURI = rawRepoURI.replace(".git", "");

            String[] splitRawRepoURI = rawRepoURI.split("/");
            organisationOrUsername = splitRawRepoURI[0];
            repositoryName = splitRawRepoURI[1];
        }
    }

    private void enlistRemotes() throws GitAPIException {
        List<RemoteConfig> remoteList = git.remoteList().call();
        log.info("Remotes: " +
                remoteList.stream()
                        .map(remote -> String.format(
                                "%s: uri: %s: pushUri: %s", remote.getName(), remote.getURIs(), remote.getPushURIs()))
                        .collect(Collectors.toList()));
    }

    private void addRemoteForHttpsURI() throws URISyntaxException, GitAPIException {
        String repoURI = String.format(
                "https://github.com/%s/%s.git",
                getOrganisationOrUsername(),
                getRepositoryName()
        );
        RemoteAddCommand remoteAddCommand = git.remoteAdd();
        remoteAddCommand.setName(HTTPS_ORIGIN);
        remoteAddCommand.setUri(new URIish(repoURI));
        RemoteConfig remoteConfig = remoteAddCommand.call();
        remoteConfig.addPushURI(new URIish(repoURI));
    }
}
