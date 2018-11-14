package io.meterian.scm.gerrit;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gerrit.extensions.api.changes.ReviewInput.RobotCommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.common.FixSuggestionInfo;
import com.meterian.common.io.SimpleFileCompare.Diff;

public class GerritRoboComment {

    private static final String NEWLINE = "\n";

    public static class Entry {
        
        public final int lineno;
        public final String message;
        public final List<String> lines;
       
        public Entry(int lineno, List<String> lines, String message) {
            this.lineno = lineno;
            this.lines = lines;
            this.message = message;
        }

        @Override
        public String toString() {
            return "Comment [lineno=" + lineno + ", lines=" + lines+ ", message=" + message + "]";
        }
        
        private String replacement() {
            return lines.stream().collect(Collectors.joining(NEWLINE));
        }

        public Range range() {
            Comment.Range range = new Comment.Range();
            
            if (lines.size() == 1) {
                // change of existing line
                range.startLine = lineno;
                range.startCharacter = 0;
                range.endCharacter = maxlen();
                range.endLine = lineno;
            } else {
                // insertion of multiple lines
                range.startLine = lineno;
                range.startCharacter = 0;
                range.endCharacter = 0;
                range.endLine = lineno;
            }

            return range;
        }

        private int maxlen() {
            return Collections.max(lines, Comparator.comparing(s -> s.length())).length();
        }
    }

    private final String filename;
    private final List<Entry> entries;
    private final URI reportUrl;
   
    public GerritRoboComment(String file, List<Diff> diffs, URI reportUrl) {
        super();
        this.filename = file;
        this.entries = Collections.unmodifiableList(createEntries(diffs));
        this.reportUrl = reportUrl;
    }

    public String filename() {
        return filename;
    }

    @Override
    public String toString() {
        return "GerritRoboComment [file=" + filename + ", entries=" + entries + "]";
    }

    private static List<Entry> createEntries(List<Diff> diffs) {
        List<Entry> comments = new ArrayList<>();
        for (Diff diff : diffs) {
            comments.add(new Entry(
                (diff.lines.size() == 1) ? diff.lineNumber : diff.lineNumber-1, 
                diff.lines, 
                "Vulnerable library, please patch."));
        }
        return comments;
    }

    public List<RobotCommentInput> asRobotCommentInput() {
        List<RobotCommentInput> comments = new ArrayList<>();
        for (Entry entry: entries) {
            FixReplacementInfo replInfo = createFixReplacementInfo(entry);
            FixSuggestionInfo suggInfo = createFixSuggestionInfo(entry, replInfo);
            RobotCommentInput comment = createRobotCommentInput(entry, suggInfo);
            comments.add(comment);
        }
        
        return comments;
    }

    private RobotCommentInput createRobotCommentInput(Entry entry, FixSuggestionInfo... suggInfos) {

        RobotCommentInput in = new RobotCommentInput();
        in.robotId = "meterian";
        in.robotRunId = "meterian";
        in.line = entry.lineno;
        in.range = entry.range();
        in.message = "Vulnerable library, please replace with:"+NEWLINE+NEWLINE+entry.replacement();
        in.path = filename;

        in.url = reportUrl.toString();
        in.fixSuggestions = Arrays.asList(suggInfos);

        return in;
    }

    private FixSuggestionInfo createFixSuggestionInfo(Entry entry, FixReplacementInfo... fixReplacementInfos) {
        FixSuggestionInfo newFixSuggestionInfo = new FixSuggestionInfo();
        newFixSuggestionInfo.fixId = UUID.randomUUID().toString();
        newFixSuggestionInfo.description = "We suggest you fix the license.";
        newFixSuggestionInfo.replacements = Arrays.asList(fixReplacementInfos);
        return newFixSuggestionInfo;
    }

    private FixReplacementInfo createFixReplacementInfo(Entry entry) {
        FixReplacementInfo newFixReplacementInfo = new FixReplacementInfo();
        newFixReplacementInfo.path = filename;
        newFixReplacementInfo.replacement = entry.replacement();
        newFixReplacementInfo.range = entry.range();
        return newFixReplacementInfo;
    }

}

