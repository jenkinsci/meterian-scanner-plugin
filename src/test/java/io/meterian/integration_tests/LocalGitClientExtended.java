package io.meterian.integration_tests;

import io.meterian.jenkins.autofixfeature.git.LocalGitClient;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE;

public class LocalGitClientExtended extends LocalGitClient {
    private static final String REFS_REMOTE = "refs/remotes/origin/";
    private final PrintStream jenkinsLogger;

    public LocalGitClientExtended(String pathToRepo,
                           String meterianGithubUser,
                           String meterianGithubToken,
                           String meterianGithubEmail,
                           PrintStream jenkinsLogger) {
        super(pathToRepo,
                meterianGithubUser,
                meterianGithubToken,
                meterianGithubEmail,
                jenkinsLogger);
        this.jenkinsLogger = jenkinsLogger;
    }

    @Override
    public void pushBranchToRemoteRepo() throws GitAPIException {
        git().fetch()
                .setRemote(HTTPS_ORIGIN)
                .setRemoveDeletedRefs(true)
                .call();
        git().push()
                .setRemote(HTTPS_ORIGIN)
                .setCredentialsProvider(credentialsProvider)
                .call();
        jenkinsLogger.println("Finished pushing branch to remote repo");
    }

    public List<String> getRemoteBranches() throws GitAPIException {
        git().fetch()
                .setRemoveDeletedRefs(true)
                .call();
        List<Ref> remoteList = git()
                .branchList()
                .setListMode(REMOTE)
                .call();
        List<String> remoteBranches = remoteList
                .stream()
                .filter(ref -> ref.getName().contains(REFS_REMOTE))
                .map(Ref::getName)
                .map(this::stripOffRefsPrefix)
                .collect(Collectors.toList());

        jenkinsLogger.println(String.format("Remote branches: %s", remoteBranches));

        return remoteBranches;
    }

    private String stripOffRefsPrefix(String value) {
        return value.replace(REFS_REMOTE, "");
    }
}