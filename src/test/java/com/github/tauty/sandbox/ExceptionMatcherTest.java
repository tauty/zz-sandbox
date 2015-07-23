package com.github.tauty.sandbox;

import com.github.tauty.util.IOUtil2;
import org.junit.Test;
import tetz42.clione.common.HereDoc;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

import static com.github.tauty.sandbox.ExceptionMatcher.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static tetz42.test.Auty.*;

public class ExceptionMatcherTest {

    private static Map<String, String> doc = HereDoc.get(ExceptionMatcherTest.class);

    @Test
    public void loadConfig_test() throws Exception {
        File file = getFile("ignore_exceptions.json");
        try {
            // load initially
            lastModified = 0;
            lastChecked = 0;
            IOUtil2.writeText(file, doc.get("short_load_config_period"));
            loadConfig();
            assertEqualsWithFile(configList, this.getClass(), "loadConfig_load_correctly1");
            assertThat(LOAD_CONFIG_PERIOD, is(1100L));
            int previousHash = System.identityHashCode(configList);

            // change the config file
            Thread.sleep(1000); // wait for 1 second so as to make the lastModified of the config file newer than previous
            IOUtil2.writeText(file, doc.get("loadConfig_test"));

            // doesn't load this time because load period has not been past.
            loadConfig();
            assertThat(System.identityHashCode(configList), is(previousHash));

            // wait for the load period past.
            Thread.sleep(200);

            // load this time.
            loadConfig();
            assertThat(System.identityHashCode(configList), is(not(previousHash)));
            assertEqualsWithFile(configList, this.getClass(), "loadConfig_load_correctly2");
            previousHash = System.identityHashCode(configList);

            // wait for the load period past.
            Thread.sleep(1200);

            // doesn't load this time because the config file has not been modified.
            loadConfig();
            assertThat(System.identityHashCode(configList), is(previousHash));

        } finally {
            lastModified = 0;
            lastChecked = 0;
            IOUtil2.writeText(file, doc.get("initial"));
            loadConfig();
            assertThat(LOAD_CONFIG_PERIOD, is(60000L));
        }
    }

    @Test
    public void isMatched_false_if_parameter_is_null() throws Exception {
        assertThat(isMatched(null), is(false));
    }

    @Test
    public void isMatched_in_case_matchParentRecursively_is_true() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("matchParentRecursively_is_true"));
        assertThat(isMatched(new NullPointerException()), is(true));
        assertThat(isMatched(new RuntimeException()), is(true));
    }

    @Test
    public void isMatched_in_case_matchParentRecursively_is_false() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("matchParentRecursively_is_false"));
        assertThat(isMatched(new NullPointerException()), is(false));
        assertThat(isMatched(new RuntimeException()), is(true));
    }

    @Test
    public void isMatched_in_case_matchCauseRecursively_is_true() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("matchCauseRecursively_is_true"));
        assertThat(isMatched(new NullPointerException()), is(true));
        assertThat(isMatched(new RuntimeException(new NullPointerException())), is(true));
    }

    @Test
    public void isMatched_in_case_matchCauseRecursively_is_false() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("matchCauseRecursively_is_false"));
        assertThat(isMatched(new NullPointerException()), is(true));
        assertThat(isMatched(new RuntimeException(new NullPointerException())), is(false));
    }

    @Test
    public void isMatched_true_if_prefixOfStackTraces_is_empty() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("empty_prefixOfStackTraces"));
        assertThat(isMatched(new NullPointerException()), is(true));
    }

    @Test
    public void isMatched_true_if_all_of_prefixOfStackTraces_matched() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("all_of_elements_of_prefixOfStackTraces_matched"));
        assertThat(isMatched(new NullPointerException()), is(true));
    }

    @Test
    public void isMatched_false_if_all_of_prefixOfStackTraces_not_matched() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("all_of_prefixOfStackTraces_not_matched"));
        assertThat(isMatched(new NullPointerException()), is(false));
    }

    @Test
    public void isMatched_true_only_if_both_of_exceptionType_and_prefixOfStackTraces_matched() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("both_of_exceptionType_and_prefixOfStackTraces_matched"));
        assertThat(isMatched(new NullPointerException()), is(true));

        // false if only prefixOfStackTraces matched
        assertThat(isMatched(new SQLException()), is(false));

        loadConfigFromText(doc.get("only_exceptionType_matched"));
        assertThat(isMatched(new NullPointerException()), is(false));
    }

    @Test
    public void isMatched_true_if_one_of_configList_matched() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("one_of_configList_matched"));
        assertThat(isMatched(new NullPointerException()), is(true));
    }

    @Test
    public void isMatched_false_if_none_of_configList_matched() throws Exception {
        loadConfig();
        loadConfigFromText(doc.get("none_of_configList_matched"));
        assertThat(isMatched(new NullPointerException()), is(false));
    }
}