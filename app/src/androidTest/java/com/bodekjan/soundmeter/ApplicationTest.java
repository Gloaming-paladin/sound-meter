package com.bodekjan.soundmeter;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {

    @Test
    public void testApplicationContext() {
        // Context of the app under test
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context);
        assertTrue(context instanceof Application);
    }

    @Test
    public void testPackageName() {
        // Test that the app's package name is correct
        Context appContext = ApplicationProvider.getApplicationContext();
        assertEquals("com.bodekjan.soundmeter", appContext.getPackageName());
    }

    @Test
    public void testInstrumentationRegistry() {
        // Test instrumentation registry context
        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        assertNotNull(instrumentationContext);
    }
}