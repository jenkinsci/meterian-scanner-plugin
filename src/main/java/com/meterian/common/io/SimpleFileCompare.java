package com.meterian.common.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleFileCompare {

    public static class Diff {
        public final int lineNumber;
        public final List<String> lines;

        public Diff(int lineno, List<String> lines) {
            this.lineNumber = lineno;
            this.lines = new ArrayList<>(lines);
        }

        @Override
        public String toString() {
            return "line=" + lineNumber + ", lines=" + lines;
        }
    }

    private final File sorce;
    private final File other;

    private int maxDepth = 16;
    
    public SimpleFileCompare(File src, File dst) {
        this.sorce = src;
        this.other = dst;
    }

    public void setMaxDepth(int x) {
        maxDepth = x;
    }
    
    public List<Diff> compare() throws IOException {

        int srcStart = 0;
        int othStart = -1;

        List<String> lines = null;
        try (BufferedReader src = new BufferedReader(new FileReader(sorce))) {
            while (true) {

                srcStart++;
                String srcLine = readLine(src);
                if (srcLine == null) {
                    srcStart = 1;
                    othStart = 1;
                    break;
                }

                lines = new ArrayList<>();;
                try (BufferedReader oth = new BufferedReader(new FileReader(other));) {
                    int lineno = 0;
                    while (othStart == -1) {
                        lineno++;
                        String othLine = readLine(oth);
                        
                        if (othLine == null || lineno > maxDepth) {
                            lines = null;
                            break;
                        }

                        if (srcLine.equals(othLine)) {
                            othStart = lineno;
                            break;
                        }

                        lines.add(othLine);
                    }
                }
                
                if (othStart != -1)
                    break;
            }
        }

        List<Diff> diffs = compareFully(srcStart, othStart);
        
        if (lines != null && !lines.isEmpty()) {
            Diff diff = new Diff(1, lines);
            diffs.add(0, diff);
        }
        
        return diffs;
    }

    public List<Diff> compareFully(int srcStart, int othStart) throws IOException {
        List<Diff> diffs = new ArrayList<>();

        int lineno = srcStart-1;
        List<String> lines = new ArrayList<>();
        boolean matchingSoFar = true;

        try (BufferedReader src = new BufferedReader(new FileReader(sorce));
                BufferedReader oth = new BufferedReader(new FileReader(other));) {

            skipToLine(src, srcStart);
            skipToLine(oth, othStart);

            boolean loop = true;
            String curLine = null;
            String nxtLine = readLine(src);
            while (loop) {
                if (matchingSoFar) {
                    curLine = nxtLine;
                    if (curLine != null)
                        nxtLine = readLine(src);
                    else {
                        loop = false;
                    }

                    lineno++;
                }

                String othLine = readLine(oth);
                if (othLine == null)
                    break;

                if (matchingSoFar) {
                    if (equals(curLine, othLine))
                        continue;
                    else
                        matchingSoFar = false;
                }

                if (!matchingSoFar) {
                    if (equals(curLine, othLine)) {
                        matchingSoFar = true;
                        addDiff(diffs, lineno, lines);
                    } else if (equals(nxtLine, othLine)) {
                        addDiff(diffs, lineno, lines);
                        matchingSoFar = true;
                        nxtLine = readLine(src);
                        lineno++;
                    } else {
                        lines.add(othLine);
                    }
                }
            }
        }

        if (lines.size() != 0)
            addDiff(diffs, lineno, lines);

        return diffs;
    }

    private void skipToLine(BufferedReader reader, int startLine) throws IOException {
        int lineno = 1;
        while (lineno++ < startLine)
            readLine(reader);
    }

    private void addDiff(List<Diff> diffs, int lineno, List<String> lines) {
        diffs.add(new Diff(lineno, lines));
        lines.clear();
    }

    private boolean equals(String srcLine, String othLine) {
        if (srcLine == null && othLine == null)
            return true;
        else if (srcLine == null || othLine == null)
            return false;
        else
            return srcLine.trim().equals(othLine.trim());
    }

    private String readLine(BufferedReader src) throws IOException {
        return src.readLine();
    }

    public static void main(String[] args) throws Exception {
        File src = new File("/home/bbossola/projects/gerritforge/vert.x/pom.xml");
        File dst = new File("/home/bbossola/projects/gerritforge/vert.x/pom.xml.fix.xml");

//      File src = new File("/home/bbossola/projects/gerritforge/maven-sample/pom.xml");
//      File dst = new File("/home/bbossola/projects/gerritforge/maven-sample/pom.xml.fix.xml");

      List<Diff> diffs = new SimpleFileCompare(src, dst).compare();
        for (Diff diff : diffs) {
            System.err.println(diff);
        }
    }

}
