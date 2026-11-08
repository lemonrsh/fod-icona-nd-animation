package com.lemonrsh.fodcustomizer;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Optional, decorative fingerprint-success effect for AOSP-style AuthRippleController.
 * The original SystemUI calls always run. No touch, biometric, HBM or window flags are changed.
 */
final class UnlockEffectHook {
    private static final String TRACKED = "fod.unlock.source.tracked";
    private static final UnlockSourceTracker SOURCES = new UnlockSourceTracker();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_NO_FILE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PARSE_ERROR = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SUCCESS = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SHOWN = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_NOT_READY = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_UNAVAILABLE = new AtomicBoolean();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "FodUnlockEffectLoader");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean installed;
    private static volatile ClassLoader systemUiLoader;
    private static volatile Prepared prepared;
    private static volatile long retryAfter;
    // Accessed only on the main thread; always removed on timeout or root detach.
    private static Active active;
    private static volatile Handler mainHandler;

    private UnlockEffectHook() { }

    static void install(ClassLoader loader) {
        List<XC_MethodHook.Unhook> installedHooks = new ArrayList<>();
        try {
            Class<?> controller = XposedHelpers.findClassIfExists(
                    "com.android.systemui.biometrics.AuthRippleController", loader);
            if (controller == null) {
                FodModule.log("Unlock effect unavailable: AuthRippleController not found; using stock.");
                return;
            }
            List<Method> sources = new ArrayList<>();
            List<Method> ripples = new ArrayList<>();
            List<Method> fallbackRipples = new ArrayList<>();
            for (Method method : controller.getDeclaredMethods()) {
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                String[] parameters = parameterNames(method);
                if (UnlockHookSignatures.isSource(method.getName(), isStatic, parameters)) {
                    sources.add(method);
                } else if (UnlockHookSignatures.isFallbackRipple(method.getName(), isStatic, parameters)) {
                    fallbackRipples.add(method);
                }
            }
            Class<?> rippleView = XposedHelpers.findClassIfExists(
                    "com.android.systemui.biometrics.AuthRippleView", loader);
            if (rippleView != null) {
                for (Method method : rippleView.getDeclaredMethods()) {
                    if (UnlockHookSignatures.isRippleViewMethod(method.getName(),
                            Modifier.isStatic(method.getModifiers()), parameterNames(method))) {
                        ripples.add(method);
                    }
                }
            }
            // Hook only one path to avoid displaying two effects for a single unlock.
            if (ripples.isEmpty()) ripples.addAll(fallbackRipples);
            if (sources.isEmpty() || ripples.isEmpty()) {
                FodModule.log("Unlock effect unavailable: supported success methods not found; using stock.");
                return;
            }
            XC_MethodHook sourceHook = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    boolean entered = false;
                    try {
                        Object source = param.args[0];
                        boolean fingerprint = source instanceof Enum<?>
                                && UnlockSourceTracker.isFingerprint(((Enum<?>) source).getDeclaringClass().getName(),
                                        ((Enum<?>) source).name());
                        SOURCES.enter(fingerprint, param.thisObject);
                        entered = true;
                        param.setObjectExtra(TRACKED, Boolean.TRUE);
                    } catch (Throwable ignored) {
                        if (entered) SOURCES.exit();
                    }
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (Boolean.TRUE.equals(param.getObjectExtra(TRACKED))) SOURCES.exit();
                    } catch (Throwable ignored) { }
                }
            };
            XC_MethodHook rippleHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.hasThrowable()) return;
                        Object controller = SOURCES.fingerprintController();
                        if (controller == null) return;
                        if (LOGGED_SUCCESS.compareAndSet(false, true)) {
                            FodModule.log("Fingerprint unlock success event observed.");
                        }
                        onFingerprintSuccess(controller);
                    } catch (Throwable error) {
                        logUnavailable("Unlock effect skipped", error);
                    }
                }
            };
            for (Method method : sources) installedHooks.add(XposedBridge.hookMethod(method, sourceHook));
            for (Method method : ripples) installedHooks.add(XposedBridge.hookMethod(method, rippleHook));
            systemUiLoader = loader;
            installed = true;
            FodModule.log("Unlock success hook installed (AOSP AuthRippleController).");
        } catch (Throwable error) {
            for (XC_MethodHook.Unhook hook : installedHooks) {
                try { hook.unhook(); } catch (Throwable ignored) { }
            }
            FodModule.log("Unlock success hook unavailable (" + errorName(error) + "); using stock.");
        }
    }

    private static String[] parameterNames(Method method) {
        Class<?>[] types = method.getParameterTypes();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) names[i] = types[i].getName();
        return names;
    }

    static void onSystemUiAttached() {
        if (installed) prepareAsync();
    }

    private static void prepareAsync() {
        if (!installed || prepared != null || systemUiLoader == null
                || SystemClock.elapsedRealtime() < retryAfter || !LOADING.compareAndSet(false, true)) return;
        try {
            LOADER.execute(() -> {
                try {
                    byte[] json = FodModule.overrideFor(Slot.UNLOCK_EFFECT);
                    if (json == null) {
                        retryAfter = SystemClock.elapsedRealtime() + 5_000;
                        if (LOGGED_NO_FILE.compareAndSet(false, true)) {
                            FodModule.log("Unlock effect: no JSON selected or provider unavailable; using stock.");
                        }
                        return;
                    }
                    prepared = Prepared.load(systemUiLoader, json);
                    FodModule.log("Unlock effect JSON ready.");
                } catch (Throwable error) {
                    retryAfter = SystemClock.elapsedRealtime() + 30_000;
                    if (LOGGED_PARSE_ERROR.compareAndSet(false, true)) {
                        FodModule.log("Unlock effect JSON could not be loaded (" + errorName(error) + "); using stock.");
                    }
                } finally {
                    LOADING.set(false);
                }
            });
        } catch (Throwable error) {
            LOADING.set(false);
            logUnavailable("Unlock effect loader unavailable", error);
        }
    }

    private static void onFingerprintSuccess(Object controller) {
        if (prepared == null) {
            prepareAsync();
            if (LOGGED_NOT_READY.compareAndSet(false, true)) {
                FodModule.log("Unlock effect not ready; this unlock uses stock.");
            }
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            show(controller);
        } else {
            WeakReference<Object> reference = new WeakReference<>(controller);
            handler().post(() -> {
                Object target = reference.get();
                if (target != null) show(target);
            });
        }
    }

    private static Handler handler() {
        Handler result = mainHandler;
        if (result == null) {
            synchronized (UnlockEffectHook.class) {
                result = mainHandler;
                if (result == null) mainHandler = result = new Handler(Looper.getMainLooper());
            }
        }
        return result;
    }

    private static void show(Object controller) {
        Active next = null;
        try {
            Prepared effect = prepared;
            if (effect == null) return;
            Object ripple = XposedHelpers.getObjectField(controller, "mView");
            Object location = XposedHelpers.getObjectField(controller, "fingerprintSensorLocation");
            float radius = XposedHelpers.getFloatField(controller, "udfpsRadius");
            if (!(ripple instanceof View) || !((View) ripple).isAttachedToWindow() || radius <= 0) {
                logUnavailable("Unlock effect skipped: UDFPS view or sensor unavailable", null);
                return;
            }
            float x;
            float y;
            if (location instanceof Point) {
                x = ((Point) location).x;
                y = ((Point) location).y;
            } else if (location instanceof PointF) {
                x = ((PointF) location).x;
                y = ((PointF) location).y;
            } else {
                logUnavailable("Unlock effect skipped: sensor location unavailable", null);
                return;
            }
            View root = ((View) ripple).getRootView();
            if (!(root instanceof ViewGroup) || !root.isAttachedToWindow()) {
                logUnavailable("Unlock effect skipped: SystemUI root unavailable", null);
                return;
            }
            int[] screenOffset = new int[2];
            root.getLocationOnScreen(screenOffset);
            UnlockEffectGeometry.Box box = UnlockEffectGeometry.place(x, y, radius,
                    screenOffset[0], screenOffset[1], root.getWidth(), root.getHeight());
            if (box == null) {
                logUnavailable("Unlock effect skipped: invalid sensor or display bounds", null);
                return;
            }

            ImageView animation = (ImageView) effect.viewConstructor.newInstance(root.getContext());
            animation.setScaleType(ImageView.ScaleType.FIT_CENTER);
            animation.setClickable(false);
            animation.setFocusable(false);
            animation.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            effect.setComposition.invoke(animation, effect.composition);
            if (effect.setRepeatCount != null) effect.setRepeatCount.invoke(animation, 0);

            FrameLayout holder = new FrameLayout(root.getContext());
            holder.setClickable(false);
            holder.setFocusable(false);
            holder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            holder.addView(animation, new FrameLayout.LayoutParams(box.size, box.size));

            removeActive();
            ViewGroupOverlay overlay = ((ViewGroup) root).getOverlay();
            next = new Active(root, overlay, holder, animation, effect.cancelAnimation);
            active = next;
            overlay.add(holder);
            holder.measure(View.MeasureSpec.makeMeasureSpec(box.size, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(box.size, View.MeasureSpec.EXACTLY));
            holder.layout(box.left, box.top, box.left + box.size, box.top + box.size);
            root.addOnAttachStateChangeListener(next);
            handler().postDelayed(next, effect.durationMs);
            effect.playAnimation.invoke(animation);
            if (LOGGED_SHOWN.compareAndSet(false, true)) {
                FodModule.log("Fingerprint unlock success effect started.");
            }
        } catch (Throwable error) {
            if (next != null) next.remove();
            logUnavailable("Unlock effect could not be displayed", error);
        }
    }

    private static void removeActive() {
        if (active != null) active.remove();
    }

    private static void logUnavailable(String text, Throwable error) {
        if (LOGGED_UNAVAILABLE.compareAndSet(false, true)) {
            FodModule.log(text + (error == null ? "; using stock." : " (" + errorName(error) + "); using stock."));
        }
    }

    private static String errorName(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private static final class Active implements Runnable, View.OnAttachStateChangeListener {
        private final View root;
        private final ViewGroupOverlay overlay;
        private final View holder;
        private final View animation;
        private final Method cancel;
        private boolean removed;

        Active(View root, ViewGroupOverlay overlay, View holder, View animation, Method cancel) {
            this.root = root;
            this.overlay = overlay;
            this.holder = holder;
            this.animation = animation;
            this.cancel = cancel;
        }

        @Override public void run() { remove(); }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { remove(); }

        void remove() {
            if (removed) return;
            removed = true;
            if (active == this) active = null;
            try { handler().removeCallbacks(this); } catch (Throwable ignored) { }
            try { root.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) { }
            try { cancel.invoke(animation); } catch (Throwable ignored) { }
            try { overlay.remove(holder); } catch (Throwable ignored) { }
        }
    }

    private static final class Prepared {
        final Object composition;
        final Constructor<?> viewConstructor;
        final Method setComposition;
        final Method setRepeatCount;
        final Method playAnimation;
        final Method cancelAnimation;
        final long durationMs;

        private Prepared(Object composition, Constructor<?> viewConstructor, Method setComposition,
                         Method setRepeatCount, Method playAnimation, Method cancelAnimation,
                         long durationMs) {
            this.composition = composition;
            this.viewConstructor = viewConstructor;
            this.setComposition = setComposition;
            this.setRepeatCount = setRepeatCount;
            this.playAnimation = playAnimation;
            this.cancelAnimation = cancelAnimation;
            this.durationMs = durationMs;
        }

        static Prepared load(ClassLoader loader, byte[] json) throws Exception {
            Class<?> factory = Class.forName("com.airbnb.lottie.LottieCompositionFactory", false, loader);
            Class<?> compositionType = Class.forName("com.airbnb.lottie.LottieComposition", false, loader);
            Class<?> view = Class.forName("com.airbnb.lottie.LottieAnimationView", false, loader);
            if (!ImageView.class.isAssignableFrom(view)) throw new IllegalStateException("Unexpected Lottie view");
            Method parse = factory.getMethod("fromJsonInputStreamSync", InputStream.class, String.class);
            Object result = parse.invoke(null, new ByteArrayInputStream(json), null);
            Object composition = result.getClass().getMethod("getValue").invoke(result);
            if (composition == null || !compositionType.isInstance(composition)) {
                throw new IllegalArgumentException("Lottie composition not available");
            }
            double duration = 1500;
            try {
                Object value = compositionType.getMethod("getDuration").invoke(composition);
                if (value instanceof Number) duration = ((Number) value).doubleValue();
            } catch (ReflectiveOperationException ignored) { }
            Method repeat = null;
            try { repeat = view.getMethod("setRepeatCount", int.class); }
            catch (NoSuchMethodException ignored) { }
            return new Prepared(composition, view.getConstructor(Context.class),
                    view.getMethod("setComposition", compositionType), repeat,
                    view.getMethod("playAnimation"), view.getMethod("cancelAnimation"),
                    UnlockEffectGeometry.durationMillis(duration));
        }
    }
}
