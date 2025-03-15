package io.th.displaynoches.displaynoches;

import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import com.google.appinventor.components.annotations.*;
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;

import java.util.List;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.ViewGroup;

import android.animation.ValueAnimator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.DashPathEffect;
import android.os.Handler;
import android.os.Looper;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.RadialGradient;
import android.graphics.SweepGradient;

@DesignerComponent(
	version = 17,
	versionName = "1.0",
	description = "Developed by th using Fast. Extension to get Display Cutout information",
	iconName = "icon.png"
)
public class DisplayNoches extends AndroidNonvisibleComponent {

    private final Activity activity;

    private int cutoutOutlineColor = 0xFFFF0000; // Default red color
    private float cutoutOutlineWidth = 2.0f;
    private boolean showCutoutOutline = false;
    private View cutoutOverlay;

    private ValueAnimator outlineAnimator;
    private GestureDetector gestureDetector;
    private boolean enableCutoutGestures = false;
    private PathEffect dashEffect;
    private boolean useAnimatedOutline = false;
    private Handler periodicUpdateHandler;
    private boolean isMonitoring = false;

    private boolean useGradient = false;
    private int[] gradientColors = new int[]{0xFFFF0000, 0xFF00FF00}; // Default red to green
    private float[] gradientPositions = null;
    private int gradientType = 0; // 0=Linear, 1=Radial, 2=Sweep
    private float gradientAngle = 0f;
    private float gradientCenterX = 0.5f;
    private float gradientCenterY = 0.5f;
    private float gradientRadius = 1.0f;

    // New fields
    private long customAnimationDuration = 1000; // default duration in ms
    private java.util.Map<String, String> customProperties = new java.util.HashMap<>();

    // New field to hold a custom color list
    private int[] customColorList = new int[0];

    // Add these fields after other private fields
    private static final int CUTOUT_TYPE_UNKNOWN = 0;
    private static final int CUTOUT_TYPE_PUNCH_HOLE = 1;
    private static final int CUTOUT_TYPE_WATER_DROP = 2;
    private static final int CUTOUT_TYPE_WIDE_NOTCH = 3;
    private int detectedCutoutType = CUTOUT_TYPE_UNKNOWN;
    private Path customCutoutPath;

    public DisplayNoches(ComponentContainer container) {
        super(container.$form());
        this.activity = container.$context();
    }

    @SimpleFunction(description = "Checks if the display has a cutout (notch, hole-punch, etc.)")
    public boolean HasDisplayCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            return insets != null && insets.getDisplayCutout() != null;
        }
        return false;
    }

    @SimpleFunction(description = "Gets the safe inset at the top of the screen.")
    public int GetSafeInsetTop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetTop();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Gets the safe inset at the bottom of the screen.")
    public int GetSafeInsetBottom() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetBottom();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Gets the safe inset at the left of the screen.")
    public int GetSafeInsetLeft() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetLeft();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Gets the safe inset at the right of the screen.")
    public int GetSafeInsetRight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetRight();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Returns the bounding rectangles of the cutout area.")
    public String GetBoundingRects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                List<Rect> rects = insets.getDisplayCutout().getBoundingRects();
                return rects.toString();
            }
        }
        return "[]";
    }

    @SimpleEvent(description = "Triggered when a display cutout is detected.")
    public void OnCutoutDetected(int top, int bottom, int left, int right) {
        EventDispatcher.dispatchEvent(this, "OnCutoutDetected", top, bottom, left, right);
    }

    @SimpleFunction(description = "Triggers the OnCutoutDetected event manually.")
    public void DetectCutout() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                int top = insets.getDisplayCutout().getSafeInsetTop();
                int bottom = insets.getDisplayCutout().getSafeInsetBottom();
                int left = insets.getDisplayCutout().getSafeInsetLeft();
                int right = insets.getDisplayCutout().getSafeInsetRight();
                OnCutoutDetected(top, bottom, left, right);
            }
        }
    }

    @SimpleFunction(description = "Gets the total height of cutouts on the display.")
    public int GetCutoutHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetTop() + 
                       insets.getDisplayCutout().getSafeInsetBottom();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Gets the total width of cutouts on the display.")
    public int GetCutoutWidth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getSafeInsetLeft() + 
                       insets.getDisplayCutout().getSafeInsetRight();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Sets whether the window can extend into the cutout area.")
    public void SetLayoutInCutout(boolean allow) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            if (allow) {
                params.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } else {
                params.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            }
            activity.getWindow().setAttributes(params);
        }
    }

    @SimpleFunction(description = "Gets the number of cutouts on the display.")
    public int GetCutoutCount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                return insets.getDisplayCutout().getBoundingRects().size();
            }
        }
        return 0;
    }

    @SimpleFunction(description = "Sets the color of the cutout outline")
    public void SetCutoutOutlineColor(int color) {
        this.cutoutOutlineColor = color;
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Sets the width of the cutout outline in pixels")
    public void SetCutoutOutlineWidth(float width) {
        this.cutoutOutlineWidth = width;
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Shows or hides the cutout outline")
    public void ShowCutoutOutline(boolean show) {
        this.showCutoutOutline = show;
        if (show) {
            createCutoutOverlay();
        } else if (cutoutOverlay != null) {
            ((ViewGroup)activity.getWindow().getDecorView()).removeView(cutoutOverlay);
            cutoutOverlay = null;
        }
    }

    @SimpleFunction(description = "Enables animated outline effect for the cutout")
    public void EnableAnimatedOutline(boolean enable) {
        this.useAnimatedOutline = enable;
        if (enable) {
            if (outlineAnimator == null) {
                outlineAnimator = ValueAnimator.ofFloat(0f, 15f);
                outlineAnimator.setDuration(customAnimationDuration);
                outlineAnimator.setRepeatCount(ValueAnimator.INFINITE);
                outlineAnimator.setRepeatMode(ValueAnimator.REVERSE);
                outlineAnimator.addUpdateListener(animation -> {
                    float value = (float) animation.getAnimatedValue();
                    dashEffect = new DashPathEffect(new float[]{value, value}, 0);
                    updateCutoutOverlay();
                });
            }
            outlineAnimator.start();
        } else if (outlineAnimator != null) {
            outlineAnimator.cancel();
            dashEffect = null;
            updateCutoutOverlay();
        }
    }

    @SimpleFunction(description = "Enables gesture detection in cutout area")
    public void EnableCutoutGestures(boolean enable) {
        this.enableCutoutGestures = enable;
        if (enable && gestureDetector == null) {
            gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (isTouchInCutoutArea(e.getX(), e.getY())) {
                        OnCutoutTapped();
                        return true;
                    }
                    return false;
                }
                
                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (isTouchInCutoutArea(e.getX(), e.getY())) {
                        OnCutoutDoubleTapped();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    @SimpleEvent(description = "Triggered when cutout area is tapped")
    public void OnCutoutTapped() {
        EventDispatcher.dispatchEvent(this, "OnCutoutTapped");
    }

    @SimpleEvent(description = "Triggered when cutout area is double tapped")
    public void OnCutoutDoubleTapped() {
        EventDispatcher.dispatchEvent(this, "OnCutoutDoubleTapped");
    }

    @SimpleFunction(description = "Starts monitoring cutout changes")
    public void StartCutoutMonitoring(int intervalMs) {
        if (!isMonitoring) {
            isMonitoring = true;
            periodicUpdateHandler = new Handler(Looper.getMainLooper());
            periodicUpdateHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isMonitoring) {
                        checkCutoutChanges();
                        periodicUpdateHandler.postDelayed(this, intervalMs);
                    }
                }
            }, intervalMs);
        }
    }

    @SimpleFunction(description = "Stops monitoring cutout changes")
    public void StopCutoutMonitoring() {
        isMonitoring = false;
        if (periodicUpdateHandler != null) {
            periodicUpdateHandler.removeCallbacksAndMessages(null);
        }
    }

    @SimpleEvent(description = "Triggered when cutout configuration changes")
    public void OnCutoutConfigurationChanged(String oldConfig, String newConfig) {
        EventDispatcher.dispatchEvent(this, "OnCutoutConfigurationChanged", oldConfig, newConfig);
    }

    private String lastCutoutConfig = "";
    private void checkCutoutChanges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String currentConfig = GetBoundingRects();
            if (!currentConfig.equals(lastCutoutConfig)) {
                OnCutoutConfigurationChanged(lastCutoutConfig, currentConfig);
                lastCutoutConfig = currentConfig;
            }
        }
    }

    private boolean isTouchInCutoutArea(float x, float y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                if (customCutoutPath != null) {
                    // Use path for more accurate touch detection
                    android.graphics.RectF bounds = new android.graphics.RectF();
                    customCutoutPath.computeBounds(bounds, true);
                    // Add small padding for easier touch
                    bounds.inset(-10, -10);
                    return bounds.contains(x, y);
                } else {
                    List<Rect> rects = insets.getDisplayCutout().getBoundingRects();
                    for (Rect rect : rects) {
                        if (rect.contains((int)x, (int)y)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // Modify createCutoutOverlay() method to use custom paths
    private void createCutoutOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                // First detect the cutout type if not already detected
                if (detectedCutoutType == CUTOUT_TYPE_UNKNOWN) {
                    DetectCutoutType();
                }
                
                cutoutOverlay = new View(activity) {
                    @Override
                    protected void onDraw(Canvas canvas) {
                        super.onDraw(canvas);
                        Paint paint = new Paint();
                        paint.setAntiAlias(true); // <-- ensures smooth outlines
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(cutoutOutlineWidth);
                        paint.setPathEffect(dashEffect);

                        if (useGradient) {
                            Shader shader = createGradientShader(getWidth(), getHeight());
                            paint.setShader(shader);
                        } else {
                            paint.setColor(cutoutOutlineColor);
                        }

                        List<Rect> rects = insets.getDisplayCutout().getBoundingRects();
                        for (Rect rect : rects) {
                            if (customCutoutPath != null) {
                                // Draw the custom path based on cutout type
                                canvas.drawPath(customCutoutPath, paint);
                            } else {
                                // Fallback to default oval
                                canvas.drawOval(new android.graphics.RectF(rect), paint);
                            }
                        }
                    }
                };

                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                );

                ((ViewGroup)activity.getWindow().getDecorView()).addView(cutoutOverlay, params);
            }
        }
    }

    private Shader createGradientShader(int width, int height) {
        float cx = width * gradientCenterX;
        float cy = height * gradientCenterY;
        float radius = Math.min(width, height) * gradientRadius;
        
        switch (gradientType) {
            case 1: // Radial
                return new RadialGradient(cx, cy, radius, 
                    gradientColors, gradientPositions, Shader.TileMode.CLAMP);
            case 2: // Sweep
                return new SweepGradient(cx, cy, gradientColors, gradientPositions);
            default: // Linear
                double angleRadians = Math.toRadians(gradientAngle);
                float x1 = (float) (width * 0.5f + Math.cos(angleRadians) * width * 0.5f);
                float y1 = (float) (height * 0.5f + Math.sin(angleRadians) * height * 0.5f);
                float x2 = (float) (width * 0.5f - Math.cos(angleRadians) * width * 0.5f);
                float y2 = (float) (height * 0.5f - Math.sin(angleRadians) * height * 0.5f);
                return new LinearGradient(x1, y1, x2, y2, 
                    gradientColors, gradientPositions, Shader.TileMode.CLAMP);
        }
    }

    private void updateCutoutOverlay() {
        if (cutoutOverlay != null) {
            cutoutOverlay.invalidate();
        }
    }

    @SimpleFunction(description = "Sets the cutout area background color")
    public void SetCutoutBackgroundColor(int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            View decorView = activity.getWindow().getDecorView();
            decorView.setBackgroundColor(color);
            activity.getWindow().setAttributes(params);
        }
    }

    @SimpleFunction(description = "Gets if cutout outline is visible")
    public boolean IsCutoutOutlineVisible() {
        return showCutoutOutline;
    }

    @SimpleFunction(description = "Gets current cutout outline color")
    public int GetCutoutOutlineColor() {
        return cutoutOutlineColor;
    }

    @SimpleFunction(description = "Gets current cutout outline width")
    public float GetCutoutOutlineWidth() {
        return cutoutOutlineWidth;
    }

    @SimpleFunction(description = "Enables gradient coloring for the cutout outline")
    public void EnableGradient(boolean enable) {
        this.useGradient = enable;
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Sets gradient colors array. Example: '0xFFFF0000,0xFF00FF00' for a gradient from red to green.")
    public void SetGradientColors(String colorsArray) {
        try {
            String[] colorStrings = colorsArray.split(",");
            int[] colors = new int[colorStrings.length];
            for (int i = 0; i < colorStrings.length; i++) {
                colors[i] = Integer.parseInt(colorStrings[i].trim());
            }
            this.gradientColors = colors;
            updateCutoutOverlay();
        } catch (Exception e) {
            // Handle parsing errors
        }
    }

    @SimpleFunction(description = "Sets gradient type (0=Linear, 1=Radial, 2=Sweep)")
    public void SetGradientType(int type) {
        this.gradientType = Math.min(Math.max(type, 0), 2);
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Sets gradient angle for linear gradient (0-360 degrees)")
    public void SetGradientAngle(float angle) {
        this.gradientAngle = angle;
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Sets gradient center point (0-1 range)")
    public void SetGradientCenter(float x, float y) {
        this.gradientCenterX = Math.min(Math.max(x, 0f), 1f);
        this.gradientCenterY = Math.min(Math.max(y, 0f), 1f);
        updateCutoutOverlay();
    }

    @SimpleFunction(description = "Sets gradient radius for radial gradient (0-1 range)")
    public void SetGradientRadius(float radius) {
        this.gradientRadius = Math.min(Math.max(radius, 0f), 1f);
        updateCutoutOverlay();
    }

    // New event: triggered when a custom property changes
    @SimpleEvent(description = "Triggered when a custom property is changed")
    public void OnCustomPropertyChanged(String property, String oldValue, String newValue) {
        EventDispatcher.dispatchEvent(this, "OnCustomPropertyChanged", property, oldValue, newValue);
    }

    // Sets a custom property by key
    @SimpleFunction(description = "Sets a custom property value")
    public void SetCustomProperty(String property, String value) {
        String old = customProperties.get(property);
        customProperties.put(property, value);
        OnCustomPropertyChanged(property, old, value);
    }

    // Gets a custom property by key
    @SimpleFunction(description = "Gets a custom property value")
    public String GetCustomProperty(String property) {
        return customProperties.containsKey(property) ? customProperties.get(property) : "";
    }

    // Resets cutout customization settings to default values
    @SimpleFunction(description = "Resets all cutout customization properties to default values")
    public void ResetCutoutProperties() {
        // Reset color, width, gradient options and custom properties
        cutoutOutlineColor = 0xFFFF0000;
        cutoutOutlineWidth = 2.0f;
        useGradient = false;
        gradientColors = new int[]{0xFFFF0000, 0xFF00FF00};
        gradientPositions = null;
        gradientType = 0;
        gradientAngle = 0f;
        gradientCenterX = 0.5f;
        gradientCenterY = 0.5f;
        gradientRadius = 1.0f;
        customAnimationDuration = 1000;
        customProperties.clear();
        updateCutoutOverlay();
    }

    // Enables custom outline animation with custom duration
    @SimpleFunction(description = "Sets a custom animation duration for the cutout outline in milliseconds")
    public void SetCustomAnimationDuration(long durationMs) {
        customAnimationDuration = durationMs;
        if (outlineAnimator != null) {
            outlineAnimator.setDuration(customAnimationDuration);
        }
        // Trigger an event on animation duration change
        OnCustomPropertyChanged("AnimationDuration", "", String.valueOf(customAnimationDuration));
    }

    @SimpleFunction(description = "Gets the current custom animation duration in milliseconds")
    public long GetCustomAnimationDuration() {
        return customAnimationDuration;
    }

    @SimpleFunction(description = "Sets a custom color list using comma separated color values. Example: '0xFFFF0000,0xFF00FF00'.")
    public void SetCustomColorList(String colorsArray) {
        try {
            String[] colorStrings = colorsArray.split(",");
            int[] colors = new int[colorStrings.length];
            for (int i = 0; i < colorStrings.length; i++) {
                colors[i] = Integer.parseInt(colorStrings[i].trim());
            }
            customColorList = colors;
            // Optionally trigger a custom property changed event
            OnCustomPropertyChanged("CustomColorList", "", colorsArray);
        } catch (Exception e) {
            // Handle parsing errors
        }
    }

    @SimpleFunction(description = "Returns the custom color list as a comma separated string.")
    public String GetCustomColorList() {
        if (customColorList.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < customColorList.length; i++) {
            sb.append(String.format("0x%08X", customColorList[i]));
            if (i != customColorList.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    // Add new functions before existing functions
    @SimpleFunction(description = "Detects and returns the type of cutout (0=Unknown, 1=Punch Hole, 2=Water Drop, 3=Wide Notch)")
    public int DetectCutoutType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowInsets insets = getWindowInsets();
            if (insets != null && insets.getDisplayCutout() != null) {
                List<Rect> rects = insets.getDisplayCutout().getBoundingRects();
                if (!rects.isEmpty()) {
                    Rect rect = rects.get(0);
                    float aspectRatio = (float) rect.width() / rect.height();
                    boolean isAtTop = rect.top == 0;
                    boolean isNearEdge = rect.left < 100 || rect.right > (activity.getWindow().getDecorView().getWidth() - 100);
                    
                    // Improved detection logic
                    if (aspectRatio >= 0.9f && aspectRatio <= 1.1f && !isNearEdge) {
                        detectedCutoutType = CUTOUT_TYPE_PUNCH_HOLE;
                    } else if (aspectRatio < 0.9f && isAtTop && rect.width() < activity.getWindow().getDecorView().getWidth() / 3) {
                        detectedCutoutType = CUTOUT_TYPE_WATER_DROP;
                    } else if (isAtTop) {
                        detectedCutoutType = CUTOUT_TYPE_WIDE_NOTCH;
                    } else {
                        detectedCutoutType = CUTOUT_TYPE_UNKNOWN;
                    }
                    createCustomCutoutPath(rect);
                    return detectedCutoutType;
                }
            }
        }
        return CUTOUT_TYPE_UNKNOWN;
    }

    private void createCustomCutoutPath(Rect rect) {
        if (rect == null) return;
        
        customCutoutPath = new Path();
        float width = rect.width();
        float height = rect.height();
        
        if (width <= 0 || height <= 0) return;

        try {
            switch (detectedCutoutType) {
                case CUTOUT_TYPE_PUNCH_HOLE:
                    createPunchHolePath(rect, width, height);
                    break;
                case CUTOUT_TYPE_WATER_DROP:
                    createWaterDropPath(rect, width, height);
                    break;
                case CUTOUT_TYPE_WIDE_NOTCH:
                    createWideNotchPath(rect, width, height);
                    break;
            }
        } catch (Exception e) {
            // Fallback to simple rect if path creation fails
            customCutoutPath.addRect(new android.graphics.RectF(rect), Path.Direction.CW);
        }
    }

    private void createPunchHolePath(Rect rect, float width, float height) {
        float centerX = rect.exactCenterX();
        float centerY = rect.exactCenterY();
        float circleRadius = Math.min(width, height) / 2.0f; // full half for a proper circle
        customCutoutPath.addCircle(centerX, centerY, circleRadius, Path.Direction.CW);
    }

    private void createWaterDropPath(Rect rect, float width, float height) {
        // Use adjusted control points for a smoother water drop shape
        float startX = rect.left;
        float startY = rect.top;
        float endX = rect.right;
        float controlX = rect.left + width / 2;
        float controlY = rect.top + height * 0.9f; // deeper curve
        customCutoutPath.moveTo(startX, startY);
        customCutoutPath.quadTo(controlX, controlY, endX, startY);
        customCutoutPath.close();
    }

    private void createWideNotchPath(Rect rect, float width, float height) {
        // Use consistent corner radii for a rounded rectangle
        float cornerRadius = height * 0.3f;
        customCutoutPath.addRoundRect(
            new android.graphics.RectF(rect),
            cornerRadius, cornerRadius,
            Path.Direction.CW
        );
    }

    // Add this helper method to get cutout type as string
    @SimpleFunction(description = "Returns the current cutout type as a string description")
    public String GetCutoutTypeDescription() {
        switch (detectedCutoutType) {
            case CUTOUT_TYPE_PUNCH_HOLE:
                return "Punch Hole";
            case CUTOUT_TYPE_WATER_DROP:
                return "Water Drop";
            case CUTOUT_TYPE_WIDE_NOTCH:
                return "Wide Notch";
            default:
                return "Unknown";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private WindowInsets getWindowInsets() {
        View decorView = activity.getWindow().getDecorView();
        return decorView.getRootWindowInsets();
    }

    public void onDestroy() {
        cleanup();
    }

    private void cleanup() {
        if (outlineAnimator != null) {
            outlineAnimator.cancel();
            outlineAnimator = null;
        }
        if (periodicUpdateHandler != null) {
            periodicUpdateHandler.removeCallbacksAndMessages(null);
            periodicUpdateHandler = null;
        }
        if (cutoutOverlay != null) {
            try {
                ((ViewGroup)activity.getWindow().getDecorView()).removeView(cutoutOverlay);
            } catch (Exception e) {
                // Handle view removal error
            }
            cutoutOverlay = null;
        }
    }
}

