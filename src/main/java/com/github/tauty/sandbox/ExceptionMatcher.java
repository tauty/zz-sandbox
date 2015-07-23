/*
 * Copyright 2015 tetsuo.ohta[at]gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.tauty.sandbox;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by tetsuo.uchiumi on 7/11/15.
 */
public class ExceptionMatcher {

    private static final String STR_LITERAL_PTN_SRC = "\"(?:\\\\\"|[^\"])*\"";
    private static final Pattern STR_LITERAL_PTN = Pattern.compile(STR_LITERAL_PTN_SRC);
    private static final Pattern STR_LITERAL_IN_OBJ_PTN = Pattern.compile("}|" + STR_LITERAL_PTN_SRC);
    private static final Pattern STR_LITERAL_IN_ARY_PTN = Pattern.compile("]|" + STR_LITERAL_PTN_SRC);
    private static final Pattern BOOL_LITERAL_PTN = Pattern.compile("true|false");
    private static final Pattern NUMERIC_LITERAL_PTN = Pattern.compile("[0-9]+");
    static volatile long LOAD_CONFIG_PERIOD = 60 * 1000;

    static volatile List<Config> configList = new ArrayList<>();
    static volatile long lastModified = 0;
    static volatile long lastChecked = 0;

    static void loadConfig() {
        if (hasLoadConfigPeriodPassed()) {
            loadConfigIfModified();
        }
    }

    private static void loadConfigIfModified() {
        File file = getFile("ignore_exceptions.json");
        if (file.lastModified() > lastModified) {
            loadConfigFromFile(file);
            lastModified = file.lastModified();
        }
    }

    private static void loadConfigFromFile(File file) {
        loadConfigFromText(readTextFile(file));
    }

    static void loadConfigFromText(String jsonText) {
        configList = Collections.unmodifiableList(parseJsonToConfigList(jsonText));
    }

    static File getFile(String path) {
        URL url = ExceptionMatcher.class.getClassLoader().getResource(path);
        return new File(url.getPath());
    }

    private static boolean hasLoadConfigPeriodPassed() {
        long now = System.currentTimeMillis();
        long period = now - lastChecked;
        if (period > LOAD_CONFIG_PERIOD) {
            lastChecked = now;
            return true;
        } else {
            return false;
        }
    }

    private static String readTextFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[0xFFF];
            int len;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while (-1 != (len = in.read(buf))) {
                baos.write(buf, 0, len);
            }
            return baos.toString();
        } catch (IOException e) {
            MissingResourceException me = new MissingResourceException("The file specified can not be read.", file.getName(), "-");
            me.initCause(e);
            throw me;
        }
    }

    private static List<Config> parseJsonToConfigList(String jsonText) {
        TokenExtractor extractor = new TokenExtractor(jsonText);
        List<Config> list = new ArrayList<>();
        Config config = new Config();
        for (String key; null != (key = extractor.nextToken(STR_LITERAL_IN_OBJ_PTN)); ) {
            if (key.equals("}")) {
                list.add(config);
                config = new Config();
                continue;
            }
            key = evaluateDoubleQuoteLiteral(key);
            if (key.equals("exceptionType")) {
                config.exceptionType = evaluateDoubleQuoteLiteral(extractor.nextToken(STR_LITERAL_PTN));
            } else if (key.equals("prefixOfStackTraces")) {
                for (String e; !"]".equals(e = extractor.nextToken(STR_LITERAL_IN_ARY_PTN)); ) {
                    config.prefixOfStackTraces.add(evaluateDoubleQuoteLiteral(e));
                }
            } else if (key.equals("matchCauseRecursively")) {
                config.matchCauseRecursively = "true".equals(extractor.nextToken(BOOL_LITERAL_PTN));
            } else if (key.equals("matchParentRecursively")) {
                config.matchParentRecursively = "true".equals(extractor.nextToken(BOOL_LITERAL_PTN));
            } else if (key.equals("loadConfigPeriod")) {
                LOAD_CONFIG_PERIOD = Integer.valueOf(extractor.nextToken(NUMERIC_LITERAL_PTN));
            }
        }
        return list;
    }

    private static String evaluateDoubleQuoteLiteral(String literal) {
        String unwrapped = literal.substring(1, literal.length() - 1);
        return unwrapped.replaceAll("\\\\\"", "\"");
    }

    public static boolean isMatched(final Throwable t) {
        loadConfig();
        for (Config config : configList) {
            if (isMatched(t, config)) return true;
        }
        return false;
    }

    private static boolean isMatched(Throwable t, Config config) {
        if (t == null) return false;
        if (isNameMatched(t.getClass(), config) && isStackTraceMatched(t, config)) {
            return true;
        }
        return config.matchCauseRecursively && isMatched(t.getCause(), config);
    }

    private static boolean isNameMatched(Class<?> clazz, Config config) {
        if (clazz == Object.class) return false;
        if (clazz.getName().equals(config.exceptionType)) return true;
        return config.matchParentRecursively && isNameMatched(clazz.getSuperclass(), config);
    }

    private static boolean isStackTraceMatched(Throwable t, Config config) {
        Set<String> prefixSet = new HashSet<>(config.prefixOfStackTraces);
        for (StackTraceElement e : t.getStackTrace()) {
            removePrefixIfMatched(e, prefixSet);
            if (prefixSet.size() == 0) {
                return true; // means all prefixes are matched.
            }
        }
        return false;
    }

    private static void removePrefixIfMatched(StackTraceElement e, Set<String> prefixSet) {
        for (String prefix : prefixSet) {
            if (e.toString().startsWith(prefix)) {
                prefixSet.remove(prefix);
                return;
            }
        }
    }

    private static class TokenExtractor {

        int pos = 0;
        final String text;

        private TokenExtractor(String text) {
            this.text = text;
        }

        private final String nextToken(Pattern ptn) {
            Matcher m = ptn.matcher(text);
            if (m.find(pos)) {
                pos = m.end();
                return m.group();
            } else {
                return null;
            }
        }
    }

    private static class Config {
        private String exceptionType;
        private List<String> prefixOfStackTraces = new ArrayList<>();
        private boolean matchCauseRecursively = true;
        private boolean matchParentRecursively = true;
    }
}
