package com.lemonrsh.fodcustomizer;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.SystemClock;
import android.util.SparseArray;
import android.util.TypedValue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy Xposed entry point. All hooks are installed only inside the SystemUI process. */
public final class FodModule implements IXposedHookLoadPackage {
    private static final String TAG = "[FOD Icon & Animation] ";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final WeakHashMap<Resources, SparseArray<Slot>> RESOURCE_IDS = new WeakHashMap<>();
    private static final EnumSet<Slot> LOGGED_MISSING = EnumSet.noneOf(Slot.class);
    private static final EnumSet<Slot> LOGGED_APPLIED = EnumSet.noneOf(Slot.class);
    private static final Set<String> LOGGED_RESOURCES = new HashSet<>();
    private static final int MAX_LOGGED_RESOURCES = 100;
    private static volatile Context systemUiContext;

    private static final ReplacementCache CACHE = new ReplacementCache(FodModule::readFromProvider,
            SystemClock::elapsedRealtime, 60_000L, 5_000L);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!ResourceMatcher.isSystemUiProcess(param.packageName, param.processName)
                || !INSTALLED.compareAndSet(false, true)) return;

        captureApplication();
        XC_MethodHook drawable = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length > 0 && p.args[0] instanceof Integer) {
                    replaceDrawable(p, (Integer) p.args[0], drawableDensity(p, false));
                }
            }
        };
        XC_MethodHook inflatedDrawable = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length > 1 && p.args[1] instanceof Integer) {
                    replaceDrawable(p, (Integer) p.args[1], drawableDensity(p, true));
                }
            }
        };
        XC_MethodHook raw = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                replaceRaw(p);
            }
        };

        // loadDrawable also catches drawables inflated from XML via TypedArray on AOSP.
        boolean internalDrawable = hookResource("loadDrawable", inflatedDrawable,
                TypedValue.class, int.class, int.class, Resources.Theme.class);
        // Public entry points are fallbacks if a ROM changes the hidden implementation.
        hookResource("getDrawable", drawable, int.class);
        hookResource("getDrawable", drawable, int.class, Resources.Theme.class);
        hookResource("getDrawableForDensity", drawable, int.class, int.class);
        hookResource("getDrawableForDensity", drawable, int.class, int.class, Resources.Theme.class);
        hookResource("openRawResource", raw, int.class);
        hookResource("openRawResource", raw, int.class, TypedValue.class);
        log("Hooks installed in " + param.processName
                + (internalDrawable ? " (including XML-inflated drawables)." : " (public drawable methods only)."));
    }

    private static void captureApplication() {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.thisObject instanceof Application) {
                        Application app = (Application) p.thisObject;
                        if (ResourceMatcher.SYSTEM_UI.equals(app.getPackageName())) {
                            systemUiContext = app;
                            CACHE.clear();
                        }
                    }
                }
            });
        } catch (Throwable error) {
            // currentApplication() below is a fallback on frameworks where attach cannot be hooked.
            log("Could not hook Application.attach: " + error.getClass().getSimpleName());
        }
    }

    private static boolean hookResource(String name, XC_MethodHook callback, Object... parameterTypes) {
        Object[] arguments = Arrays.copyOf(parameterTypes, parameterTypes.length + 1);
        arguments[parameterTypes.length] = callback;
        try {
            XposedHelpers.findAndHookMethod(Resources.class, name, arguments);
            return true;
        } catch (Throwable error) {
            log("Hook unavailable: Resources." + name + " (" + error.getClass().getSimpleName() + ")");
            return false;
        }
    }

    private static int drawableDensity(XC_MethodHook.MethodHookParam p, boolean internal) {
        // getDrawableForDensity(id, density, ...) and loadDrawable(value, id, density, theme).
        if (internal && p.args.length > 2 && p.args[2] instanceof Integer) return (Integer) p.args[2];
        if (!internal && p.method != null && "getDrawableForDensity".equals(p.method.getName())
                && p.args.length > 1 && p.args[1] instanceof Integer) return (Integer) p.args[1];
        return 0;
    }

    private static void replaceDrawable(XC_MethodHook.MethodHookParam p, int id, int density) {
        try {
            if (!(p.thisObject instanceof Resources)) return;
            Resources resources = (Resources) p.thisObject;
            Slot slot = identify(resources, id);
            if (slot == null || slot.kind != Slot.Kind.PNG) return;
            byte[] data = overrideFor(slot);
            if (data == null) return;

            // Recheck bounds in SystemUI before allocating a bitmap, even though the app validates imports.
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 2048 || bounds.outHeight > 2048) return;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bitmap == null) return;
            BitmapDrawable result = new BitmapDrawable(resources, bitmap);
            if (density > 0) result.setTargetDensity(density);
            p.setResult(result);
            logAppliedOnce(slot);
        } catch (Throwable error) {
            // A failed override must leave the original method and drawable intact.
            log("Drawable override failed; using stock: " + error.getClass().getSimpleName());
        }
    }

    private static void replaceRaw(XC_MethodHook.MethodHookParam p) {
        try {
            if (!(p.thisObject instanceof Resources) || p.args.length == 0 || !(p.args[0] instanceof Integer)) return;
            Resources resources = (Resources) p.thisObject;
            int id = (Integer) p.args[0];
            Slot slot = identify(resources, id);
            if (slot == null || slot.kind != Slot.Kind.LOTTIE) return;
            byte[] data = overrideFor(slot);
            if (data == null) return;
            if (p.args.length == 2) {
                // Preserve the documented TypedValue output of this overload.
                if (!(p.args[1] instanceof TypedValue)) return;
                resources.getValue(id, (TypedValue) p.args[1], true);
            }
            p.setResult(new ByteArrayInputStream(data));
            logAppliedOnce(slot);
        } catch (Throwable error) {
            log("Animation override failed; using stock: " + error.getClass().getSimpleName());
        }
    }

    private static Slot identify(Resources resources, int id) {
        if (id == 0) return null;
        synchronized (RESOURCE_IDS) {
            SparseArray<Slot> ids = RESOURCE_IDS.get(resources);
            if (ids != null) {
                int index = ids.indexOfKey(id);
                if (index >= 0) return ids.valueAt(index); // null is a cached non-match
            }
        }
        Slot slot;
        try {
            String packageName = resources.getResourcePackageName(id);
            String type = resources.getResourceTypeName(id);
            String entryName = resources.getResourceEntryName(id);
            slot = ResourceMatcher.match(packageName, type, entryName);
            logObservedResource(packageName, type, entryName, slot);
        } catch (Resources.NotFoundException ignored) {
            return null;
        }
        synchronized (RESOURCE_IDS) {
            SparseArray<Slot> ids = RESOURCE_IDS.get(resources);
            if (ids == null) {
                ids = new SparseArray<>();
                RESOURCE_IDS.put(resources, ids);
            }
            if (ids.size() >= 2048) ids.clear();
            ids.put(id, slot);
        }
        return slot;
    }

    private static byte[] overrideFor(Slot slot) {
        Context context = systemUiContext;
        if (context == null) {
            Application app = AndroidAppHelper.currentApplication();
            if (app == null || !ResourceMatcher.SYSTEM_UI.equals(app.getPackageName())) return null;
            systemUiContext = app;
        }
        return CACHE.get(slot);
    }

    private static byte[] readFromProvider(Slot slot) throws IOException {
        Context context = systemUiContext;
        if (context == null) return null;
        try (InputStream input = context.getContentResolver().openInputStream(OverridesContract.uriFor(slot))) {
            if (input == null) {
                logMissingOnce(slot, "provider unavailable");
                return null;
            }
            return BoundedInput.readFully(input, BoundedInput.MAX_BYTES);
        } catch (FileNotFoundException e) {
            logMissingOnce(slot, "no custom file or provider unavailable");
            return null;
        } catch (SecurityException e) {
            logMissingOnce(slot, "provider access denied");
            throw e;
        } catch (IOException e) {
            logMissingOnce(slot, "could not read custom file");
            throw e;
        }
    }

    private static void logObservedResource(String packageName, String type, String entryName, Slot slot) {
        if (!ResourceDiagnostics.shouldLog(packageName, type, entryName)) return;
        String resource = type + "/" + entryName;
        synchronized (LOGGED_RESOURCES) {
            if (LOGGED_RESOURCES.size() >= MAX_LOGGED_RESOURCES || !LOGGED_RESOURCES.add(resource)) return;
        }
        // Resource names only; never log the file contents, images or biometric data.
        log("Observed " + resource + (slot == null ? " (not mapped)" : " -> " + slot.name()));
    }

    private static void logMissingOnce(Slot slot, String reason) {
        synchronized (LOGGED_MISSING) {
            if (LOGGED_MISSING.add(slot)) log(slot.name() + ": " + reason + "; using stock.");
        }
    }

    private static void logAppliedOnce(Slot slot) {
        synchronized (LOGGED_APPLIED) {
            if (LOGGED_APPLIED.add(slot)) log("Applied " + slot.name() + ".");
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }
}
