package com.meterian.common.io;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.meterian.common.io.SimpleFileCompare.Diff;

public class SimpleFileCompareTest {

    private File tmp;
    private File src;
    private File oth;

    @Before
    public void setup() {
        String sysTmp = System.getProperty("java.io.tmpdir");
        assertNotNull(sysTmp);
        tmp = new File(new File(sysTmp), "meterian-" + System.currentTimeMillis());
        tmp.mkdirs();
        
        src = new File(tmp, "src.txt");
        oth = new File(tmp, "oth.txt");
    }

    @After
    public void teardn() throws IOException {
        FileUtils.deleteDirectory(tmp);
    }

    @Test
    public void shouldFindOneLineAddedInTheMiddle() throws IOException {
        write(src,
                "one",
                "two",
                "tre");

        write(oth,
                "one",
                "two",
                "yadda",
                "tre");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(3, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }

    @Test
    public void shouldFindOneLineAddedAtTheEnd() throws IOException {
        write(src,
                "one",
                "two",
                "tre");

        write(oth,
                "one",
                "two",
                "tre",
                "yadda");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(4, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }

    @Test
    public void shouldFindOneDifferentLineInTheMiddle() throws IOException {
        write(src,
                "one",
                "two",
                "tre");

        write(oth,
                "one",
                "yadda",
                "tre");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(2, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }


    @Test
    public void shouldFindMultipleDifferentLineInTheMiddle() throws IOException {
        write(src,
                "one",
                "two",
                "tre");

        write(oth,
                "one",
                "yadda",
                "dabba",
                "du",
                "tre");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(2, diff.lineNumber);
        assertEquals(Arrays.asList("yadda", "dabba", "du"), diff.lines);
    }


    @Test
    public void shouldFindOneDifferentLineInTheMiddleWithSpaces() throws IOException {
        write(src,
                "one   ",
                "\ttwo",
                "tre");

        write(oth,
                "\tone",
                "yadda",
                "\t\ttre");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(2, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }

    @Test
    public void shouldFindOneDifferentLineAtTheStart() throws IOException {
        write(src,
                "one",
                "two",
                "tre");

        write(oth,
                "yadda",
                "two",
                "tre");

        List<Diff> diffs = compare();

        assertEquals(1, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(1, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }

    @Test
    public void shouldFindMultipleDifferences() throws IOException {
        write(src,
                "alfa",
                "beta",
                "two",
                "tre",
                "sei",
                "lei");

        write(oth,
                "yadda",
                "two",
                "tre",
                "xxx",
                "lei");
        
        List<Diff> diffs = compare();

        assertEquals(2, diffs.size());
        
        Diff diff = diffs.get(0);
        assertEquals(1, diff.lineNumber);
        assertEquals(Arrays.asList("yadda"), diff.lines);
    }

    private List<Diff> compare() throws IOException {
        SimpleFileCompare fc = new SimpleFileCompare(src,oth);
        List<Diff> diffs = fc.compare();
        return diffs;
    }
    

    private void write(File file, String... lines) throws IOException {
        Files.write(file.toPath(), Arrays.asList(lines), UTF_8, CREATE, APPEND);
    }
}
