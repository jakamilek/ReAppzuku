package com.gree1d.reappzuku.manager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses activity state, not arbitrary package mentions in the task history. */
public final class ForegroundAppDetector {
    private static final Pattern ACTIVITY_HEADER = Pattern.compile(
            "^\\s*(?:\\*?\\s*Hist\\s+#\\d+:\\s*|\\*\\s*)ActivityRecord\\{[^}]*}");
    private static final Pattern SUMMARY = Pattern.compile(
            "^\\s*(?:mResumedActivity|ResumedActivity|topResumedActivity|mTopResumedActivity|mFocusedActivity)\\s*[:=]\\s*(ActivityRecord\\{[^}]*}|null)\\s*$");
    private static final Pattern COMPONENT = Pattern.compile(
            "\\bu\\d+\\s+([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*)/[^\\s}]+");
    private static final Pattern STATE = Pattern.compile(
            "^(?:mState|state)=(INITIALIZING|STARTED|RESUMED|PAUSING|PAUSED|STOPPING|STOPPED|FINISHING|DESTROYING|DESTROYED|RESTARTING_PROCESS)(?:\\s|$)");
    private static final Pattern VISIBILITY_LINE = Pattern.compile(
            "^(?:mVisibleRequested|mVisible|visible|waitingVisible|nowVisible)=(?:true|false)(?:\\s|$)");
    private static final Pattern VISIBLE = Pattern.compile(
            "(?:^|\\s)(?:mVisibleRequested|mVisible|visible|nowVisible)=true(?:\\s|$)");

    private ForegroundAppDetector() {}

    public static final class Snapshot {
        private final boolean interactive;
        private final boolean reliable;
        private final Set<String> packages;

        private Snapshot(boolean interactive, boolean reliable, Set<String> packages) {
            this.interactive = interactive;
            this.reliable = reliable;
            this.packages = Collections.unmodifiableSet(new HashSet<>(packages));
        }

        public boolean isInteractive() { return interactive; }
        public boolean isReliable() { return reliable; }
        public boolean protects(String packageName) { return packages.contains(packageName); }
    }

    /**
     * Activity records can remain RESUMED/visible briefly after ACTION_SCREEN_OFF.
     * They must not exempt the last used app while the device is non-interactive.
     * With the screen on, an unknown/failed dump is not permission to kill apps.
     */
    public static Snapshot parse(String dump, boolean interactive) {
        Set<String> protectedPackages = new HashSet<>();
        if (!interactive) return new Snapshot(false, true, protectedPackages);
        if (dump == null || dump.trim().isEmpty()) {
            return new Snapshot(true, false, protectedPackages);
        }

        boolean recognizedState = false;
        boolean incompleteRecord = false;
        boolean recordHasState = false;
        String recordPackage = null;
        int recordIndent = -1;
        int fieldIndent = -1;

        for (String line : dump.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int indent = indentation(line);

            // Do not carry one activity's state into the next task/summary block.
            if (recordPackage != null && (indent <= recordIndent
                    || (fieldIndent >= 0 && indent < fieldIndent))) {
                if (!recordHasState) incompleteRecord = true;
                recordPackage = null;
                fieldIndent = -1;
            }

            Matcher summary = SUMMARY.matcher(line);
            if (summary.matches()) {
                if ("null".equals(summary.group(1))) {
                    recognizedState = true;
                } else {
                    String pkg = componentPackage(summary.group(1));
                    if (pkg != null) {
                        recognizedState = true;
                        protectedPackages.add(pkg);
                    }
                }
                continue;
            }

            Matcher history = ACTIVITY_HEADER.matcher(line);
            if (history.find()) {
                if (recordPackage != null && !recordHasState) incompleteRecord = true;
                recordPackage = componentPackage(history.group());
                if (recordPackage == null) incompleteRecord = true;
                recordHasState = false;
                recordIndent = indent;
                fieldIndent = -1;
                continue;
            }

            if (recordPackage == null) continue;
            // AOSP writes packageName first. Only inspect direct record fields,
            // never similarly named keys inside a nested Intent/Bundle dump.
            if (fieldIndent == -1) fieldIndent = indent;
            if (indent != fieldIndent) continue;

            Matcher state = STATE.matcher(trimmed);
            if (state.find()) {
                recognizedState = true;
                recordHasState = true;
                if ("RESUMED".equals(state.group(1))) protectedPackages.add(recordPackage);
            } else if (VISIBILITY_LINE.matcher(trimmed).find()) {
                recognizedState = true;
                recordHasState = true;
                // Paused activities can still be visible in split screen or PiP.
                if (VISIBLE.matcher(trimmed).find()) protectedPackages.add(recordPackage);
            }
        }
        if (recordPackage != null && !recordHasState) incompleteRecord = true;
        return new Snapshot(true, recognizedState && !incompleteRecord, protectedPackages);
    }

    private static String componentPackage(String record) {
        Matcher component = COMPONENT.matcher(record);
        return component.find() ? component.group(1) : null;
    }

    private static int indentation(String line) {
        int count = 0;
        while (count < line.length() && Character.isWhitespace(line.charAt(count))) count++;
        return count;
    }
}
