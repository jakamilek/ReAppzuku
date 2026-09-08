package com.gree1d.reappzuku.manager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/** Synthetic, privacy-safe fixtures model AOSP's activity dump fields. */
public class ForegroundAppDetectorTest {
    public static void main(String[] args) throws Exception {
        ForegroundAppDetectorTest tests = new ForegroundAppDetectorTest();
        Method[] methods = ForegroundAppDetectorTest.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        int passed = 0;
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == void.class && method.getParameterCount() == 0) {
                method.invoke(tests);
                System.out.println("PASS " + method.getName());
                passed++;
            }
        }
        if (passed == 0) throw new AssertionError("No regression tests were executed");
        System.out.println("PASSED " + passed + " regression tests");
    }

    private static void assertTrue(boolean value) { assertTrue("Expected true", value); }
    private static void assertTrue(String message, boolean value) {
        if (!value) throw new AssertionError(message);
    }
    private static void assertFalse(boolean value) { assertTrue("Expected false", !value); }
    private static void assertFalse(String message, boolean value) { assertTrue(message, !value); }

    private static String activity(String pkg, String state, String visibility) {
        return "    * Hist #0: ActivityRecord{abc u0 " + pkg + "/.MainActivity t42}\n"
                + "      packageName=" + pkg + " processName=" + pkg + "\n"
                + "      state=" + state + " delayedResume=false finishing=false\n"
                + "      " + visibility + "\n";
    }

    public void allegroInHistoryIsNotForegroundAfterGoingHome() {
        String dump = "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n"
                + activity("com.example.launcher", "RESUMED", "mVisibleRequested=true mVisible=true")
                + activity("pl.allegro", "STOPPED", "mVisibleRequested=false mVisible=false mClientVisible=true reportedVisible=true")
                + "  mResumedActivity: ActivityRecord{def u0 com.example.launcher/.Home t1}\n";
        ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(dump, true);
        assertTrue(result.isReliable());
        assertTrue(result.protects("com.example.launcher"));
        assertFalse(result.protects("pl.allegro"));
    }

    public void genuinelyResumedAllegroIsProtected() {
        assertTrue(ForegroundAppDetector.parse(activity("pl.allegro", "RESUMED",
                "mVisibleRequested=true mVisible=true"), true).protects("pl.allegro"));
    }

    public void staleResumedActivityDoesNotBlockScreenOffKill() {
        String dump = activity("pl.allegro", "RESUMED", "mVisibleRequested=true mVisible=true")
                + "topResumedActivity=ActivityRecord{abc u0 pl.allegro/.MainActivity t42}\n";
        ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(dump, false);
        assertTrue(result.isReliable());
        assertFalse(result.isInteractive());
        assertFalse(result.protects("pl.allegro"));
    }

    public void pausedPictureInPictureRemainsProtected() {
        assertTrue(ForegroundAppDetector.parse(activity("com.example.video", "PAUSED",
                "mVisibleRequested=true mVisible=true mClientVisible=true"), true)
                .protects("com.example.video"));
    }

    public void bothSplitScreenActivitiesAreProtected() {
        String dump = activity("com.example.left", "RESUMED", "mVisibleRequested=true mVisible=true")
                + activity("com.example.right", "PAUSED", "visible=true nowVisible=true");
        ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(dump, true);
        assertTrue(result.isReliable());
        assertTrue(result.protects("com.example.left"));
        assertTrue(result.protects("com.example.right"));
    }

    public void oldAndNewResumedSummariesUseExactPackages() {
        for (String label : new String[]{"mResumedActivity:", "ResumedActivity:",
                "topResumedActivity=", "mTopResumedActivity=", "mFocusedActivity:"}) {
            ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(
                    "  " + label + " ActivityRecord{abc u0 pl.allegro.extra/.Main t9}\n", true);
            assertTrue(label, result.isReliable());
            assertTrue(label, result.protects("pl.allegro.extra"));
            assertFalse(label, result.protects("pl.allegro"));
        }
    }

    public void pausedAndLastResumedSummariesAreNotCurrentForeground() {
        String dump = "mResumedActivity: null\n"
                + "mLastResumedActivity: ActivityRecord{abc u0 pl.allegro/.Main t42}\n"
                + "topPausingActivity=ActivityRecord{abc u0 pl.allegro/.Main t42}\n";
        ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(dump, true);
        assertTrue(result.isReliable());
        assertFalse(result.protects("pl.allegro"));
    }

    public void unrelatedIntentPackageMentionDoesNotProtectTarget() {
        String dump = activity("com.example.launcher", "RESUMED", "mVisible=true")
                + "      intent={act=android.intent.action.VIEW cmp=pl.allegro/.Main}\n";
        assertFalse(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void clientAndReportedVisibilityAloneDoNotProtectStoppedApp() {
        String dump = activity("pl.allegro", "STOPPED",
                "mVisibleRequested=false mVisible=false mClientVisible=true reportedVisible=true");
        assertFalse(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void nestedStateAndVisibilityDoNotLeakIntoActivity() {
        String dump = activity("pl.allegro", "STOPPED", "mVisible=false")
                + "      NestedObject:\n        state=RESUMED\n        mVisible=true\n";
        assertFalse(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void taskVisibilityDoesNotLeakIntoPreviousActivity() {
        String dump = activity("pl.allegro", "STOPPED", "mVisible=false")
                + "  * Task{other}\n      visible=true\n      state=RESUMED\n";
        assertFalse(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void malformedOrFailedDumpIsNotPermissionToKillWhileAwake() {
        for (String dump : new String[]{null, "", "Permission Denial: can't dump ActivityManager",
                "ACTIVITY MANAGER ACTIVITIES\nnew unknown OEM format\n"}) {
            assertFalse(ForegroundAppDetector.parse(dump, true).isReliable());
        }
    }

    public void nonInteractiveDeviceDoesNotRequireAnActivityDump() {
        assertTrue(ForegroundAppDetector.parse(null, false).isReliable());
        assertFalse(ForegroundAppDetector.parse(null, false).protects("pl.allegro"));
    }

    public void truncatedActivityRecordAbortsAnAwakeCycle() {
        String dump = activity("pl.allegro", "STOPPED", "mVisible=false")
                + "    * Hist #0: ActivityRecord{def u0 com.example.editor/.Edit t43}\n"
                + "      packageName=com.example.editor\n";
        assertFalse(ForegroundAppDetector.parse(dump, true).isReliable());
    }

    public void oldVisibilityAndMStateFieldsAreSupported() {
        String dump = activity("pl.allegro", "PAUSED", "waitingVisible=false nowVisible=true")
                .replace("state=PAUSED", "mState=PAUSED").replace("\n", "\r\n");
        assertTrue(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void visibleAppInAnotherUserRemainsProtectedFromPackageWideKill() {
        String dump = "topResumedActivity=ActivityRecord{abc u10 pl.allegro/.Main t42}\n";
        assertTrue(ForegroundAppDetector.parse(dump, true).protects("pl.allegro"));
    }

    public void androidSystemPackageIsAValidComponent() {
        assertTrue(ForegroundAppDetector.parse(
                "mResumedActivity: ActivityRecord{abc u0 android/.ResolverActivity t42}\n", true)
                .protects("android"));
    }

    public void activityHeaderWithoutHistoryNumberIsSupported() {
        String dump = activity("com.example.video", "PAUSED", "mVisible=true")
                .replace("* Hist #0:", "*");
        ForegroundAppDetector.Snapshot result = ForegroundAppDetector.parse(dump, true);
        assertTrue(result.isReliable());
        assertTrue(result.protects("com.example.video"));
    }
}
