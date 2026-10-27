package com.farmos.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[36])
class LauncherIconContractTest {
    @Test fun launcherUsesTheFarmAnimalArtwork() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val info=context.packageManager.getApplicationInfo(context.packageName,0)
        assertEquals("Animal Farm",context.packageManager.getApplicationLabel(info).toString())
        assertNotEquals(0,info.icon)
        assertNotNull(context.packageManager.getApplicationIcon(info))
        val foreground=BitmapFactory.decodeResource(context.resources,R.drawable.ic_launcher_foreground)
        assertNotNull(foreground)
        assertEquals(432,foreground.width)
        assertEquals(432,foreground.height)
        assertEquals(0,foreground.getPixel(0,0) ushr 24)
    }
}
