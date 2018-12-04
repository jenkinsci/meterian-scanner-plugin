package io.meterian.jenkins.glue.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class LocalGitClient {

    private final Git git;
    private String branchName;

    public static void main(String[] args) throws IOException, GitAPIException {
        String pathToRepo = "/home/satyasai/git-repos/meterian/jenkins-plugin/work/workspace/ultiPipeline-autofix_master-R7BPEVOKUIKKVF72USMTYF2U6Z2VP6KEXA3A7LEZGIUW4BAN6PLA";
        LocalGitClient localGitClient = new LocalGitClient(pathToRepo);
        System.out.println(localGitClient.createBranch("fixed-by-meterian-" + addSomeSuffix()));
        System.out.println(localGitClient.addChangedFileToBranch("pom.xml"));
        System.out.println(localGitClient.commitChanges("Meterian.com", "Meterian.com", "info@meterian.com", "Fixes applied to pom.xml via meterian"));
        System.out.println(localGitClient.pushBranchToRemoteRepo());
    }

    public LocalGitClient(String pathToRepo) throws IOException {
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

    public void applyCommits() throws GitAPIException {
        Ref branchRef = createBranch("fixed-by-meterian-" + addSomeSuffix());
        branchName = branchRef.getName();
        addChangedFileToBranch("pom.xml");

        // TODO: need correct info for these fields
        commitChanges("Meterian.com",
                "Meterian.com",
                "info@meterian.com",
                "Fixes applied to pom.xml via meterian");
    }

    public String getBranchName() {
        return branchName;
    }

    private Ref createBranch(String branchName) throws GitAPIException {
        Ref branchCreateRef = git.branchCreate()
                .setName(branchName)
                .call();
        Ref checkoutRef = null;
        if (branchCreateRef != null) {
            checkoutRef = git.checkout()
                    .setName(branchName)
                    .call();
        }
        return checkoutRef;
    }

    private DirCache addChangedFileToBranch(String fileName) throws GitAPIException {
        return git
                .add()
                // TODO: find out a better way to find changed files in a branch IF the below isn't the right way, since this is Java / Maven specific, it will do for now
                .addFilepattern(fileName)
                .call();
    }

    private RevCommit commitChanges(String authorName,
                                    String committerName,
                                    String email,
                                    String commitMessage) throws GitAPIException {
        return git
                .commit()
                .setAuthor(authorName, email)
                .setCommitter(committerName, email)
                .setMessage(commitMessage)
                .call();
    }

    private Iterable<PushResult> pushBranchToRemoteRepo() throws GitAPIException {
        return git.push()
                .setForce(true)
                .call();
    }

    private static String addSomeSuffix() {
        return String.valueOf(
                new Random().nextInt(
                        (int) System.currentTimeMillis()
                )
        );
    }
}
