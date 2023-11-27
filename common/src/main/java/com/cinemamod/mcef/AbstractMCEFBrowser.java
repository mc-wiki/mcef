package com.cinemamod.mcef;

import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import net.minecraft.client.Minecraft;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

import static org.lwjgl.glfw.GLFW.*;

public abstract class AbstractMCEFBrowser extends CefBrowserOsr {
    /**
     * Stores information about drag & drop.
     */
    private final MCEFDragContext dragContext = new MCEFDragContext();

    /**
     * A listener that defines that happens when a cursor changes in the browser.
     * E.g. when you've hovered over a button, an input box, are selecting text, etc...
     * A default listener is created in the constructor that sets the cursor type to
     * the appropriate cursor based on the event.
     */
    private MCEFCursorChangeListener cursorChangeListener;

    /**
     * Whether MCEF should mimic the controls of a typical web browser.
     * E.g. CTRL+R for reload, CTRL+Left for back, CTRL+Right for forward, etc...
     */
    private boolean browserControls = true;

    /**
     * A bitset representing what mouse buttons are currently pressed.
     * CEF is a bit odd and implements mouse buttons as a part of modifier flags.
     */
    private int btnMask = 0;

    public AbstractMCEFBrowser(CefClient client, String url, boolean transparent, CefRequestContext context) {
        super(client, url, transparent, context);

        cursorChangeListener = (cefCursorID) -> setCursor(CefCursorType.fromId(cefCursorID));
    }

    public MCEFCursorChangeListener getCursorChangeListener() {
        return cursorChangeListener;
    }

    public void setCursorChangeListener(MCEFCursorChangeListener cursorChangeListener) {
        this.cursorChangeListener = cursorChangeListener;
    }

    public boolean usingBrowserControls() {
        return browserControls;
    }

    /**
     * Enabling browser controls tells MCEF to mimic the behavior of an actual browser.
     * CTRL+R for reload, CTRL+Left for back, CTRL+Right for forward, etc...
     *
     * @param browserControls whether browser controls should be enabled
     * @return the browser instance
     */
    public AbstractMCEFBrowser useBrowserControls(boolean browserControls) {
        this.browserControls = browserControls;
        return this;
    }

    public MCEFDragContext getDragContext() {
        return dragContext;
    }

    // Inputs
    public void sendKeyPress(int keyCode, long scanCode, int modifiers) {
        if (browserControls) {
            if (modifiers == GLFW_MOD_CONTROL) {
                if (keyCode == GLFW_KEY_R) {
                    reload();
                    return;
                } else if (keyCode == GLFW_KEY_EQUAL) {
                    if (getZoomLevel() < 9) setZoomLevel(getZoomLevel() + 1);
                    return;
                } else if (keyCode == GLFW_KEY_MINUS) {
                    if (getZoomLevel() > -9) setZoomLevel(getZoomLevel() - 1);
                    return;
                } else if (keyCode == GLFW_KEY_0) {
                    setZoomLevel(0);
                    return;
                }
            } else if (modifiers == GLFW_MOD_ALT) {
                if (keyCode == GLFW_KEY_LEFT && canGoBack()) {
                    goBack();
                    return;
                } else if (keyCode == GLFW_KEY_RIGHT && canGoForward()) {
                    goForward();
                    return;
                }
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_PRESS, keyCode, (char) keyCode, modifiers);
        e.scancode = scanCode;
        sendKeyEvent(e);
    }

    public void sendKeyRelease(int keyCode, long scanCode, int modifiers) {
        if (browserControls) {
            if (modifiers == GLFW_MOD_CONTROL) {
                if (keyCode == GLFW_KEY_R) return;
                else if (keyCode == GLFW_KEY_EQUAL) return;
                else if (keyCode == GLFW_KEY_MINUS) return;
                else if (keyCode == GLFW_KEY_0) return;
            } else if (modifiers == GLFW_MOD_ALT) {
                if (keyCode == GLFW_KEY_LEFT && canGoBack()) return;
                else if (keyCode == GLFW_KEY_RIGHT && canGoForward()) return;
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_RELEASE, keyCode, (char) keyCode, modifiers);
        e.scancode = scanCode;
        sendKeyEvent(e);
    }

    public void sendKeyTyped(char c, int modifiers) {
        if (browserControls) {
            if (modifiers == GLFW_MOD_CONTROL) {
                if ((int) c == GLFW_KEY_R) return;
                else if ((int) c == GLFW_KEY_EQUAL) return;
                else if ((int) c == GLFW_KEY_MINUS) return;
                else if ((int) c == GLFW_KEY_0) return;
            } else if (modifiers == GLFW_MOD_ALT) {
                if ((int) c == GLFW_KEY_LEFT && canGoBack()) return;
                else if ((int) c == GLFW_KEY_RIGHT && canGoForward()) return;
            }
        }

        CefKeyEvent e = new CefKeyEvent(CefKeyEvent.KEY_TYPE, c, c, modifiers);
        sendKeyEvent(e);
    }

    public void sendMouseMove(int mouseX, int mouseY) {
        CefMouseEvent e = new CefMouseEvent(CefMouseEvent.MOUSE_MOVED, mouseX, mouseY, 0, 0, dragContext.getVirtualModifiers(btnMask));
        sendMouseEvent(e);

        if (dragContext.isDragging())
            this.dragTargetDragOver(new Point(mouseX, mouseY), 0, dragContext.getMask());
    }

    // TODO: it may be necessary to add modifiers here
    public void sendMousePress(int mouseX, int mouseY, int button) {
        // for some reason, middle and right are swapped in MC
        if (button == 1) button = 2;
        else if (button == 2) button = 1;

        if (button == 0) btnMask |= CefMouseEvent.BUTTON1_MASK;
        else if (button == 1) btnMask |= CefMouseEvent.BUTTON2_MASK;
        else if (button == 2) btnMask |= CefMouseEvent.BUTTON3_MASK;

        CefMouseEvent e = new CefMouseEvent(GLFW_PRESS, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);
    }

    // TODO: it may be necessary to add modifiers here
    public void sendMouseRelease(int mouseX, int mouseY, int button) {
        // For some reason, middle and right are swapped in MC
        if (button == 1) button = 2;
        else if (button == 2) button = 1;

        if (button == 0 && (btnMask & CefMouseEvent.BUTTON1_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON1_MASK;
        else if (button == 1 && (btnMask & CefMouseEvent.BUTTON2_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON2_MASK;
        else if (button == 2 && (btnMask & CefMouseEvent.BUTTON3_MASK) != 0) btnMask ^= CefMouseEvent.BUTTON3_MASK;

        CefMouseEvent e = new CefMouseEvent(GLFW_RELEASE, mouseX, mouseY, 1, button, btnMask);
        sendMouseEvent(e);

        // drag&drop
        if (dragContext.isDragging()) {
            if (button == 0) {
                finishDragging(mouseX, mouseY);
            }
        }
    }

    // TODO: smooth scrolling
    public void sendMouseWheel(int mouseX, int mouseY, double amount, int modifiers) {
        if (browserControls) {
            if ((modifiers & GLFW_MOD_CONTROL) != 0) {
                if (amount > 0) {
                    if (getZoomLevel() < 9) setZoomLevel(getZoomLevel() + 1);
                } else if (getZoomLevel() > -9) setZoomLevel(getZoomLevel() - 1);
                return;
            }
        }

        // macOS generally has a slow scroll speed that feels more natural with their magic mice / trackpads
        if (!MCEFPlatform.getPlatform().isMacOS()) {
            // This removes the feeling of "smooth scroll"
            if (amount < 0) {
                amount = Math.floor(amount);
            } else {
                amount = Math.ceil(amount);
            }

            // This feels about equivalent to chromium with smooth scrolling disabled -ds58
            amount = amount * 3;
        }

        CefMouseWheelEvent e = new CefMouseWheelEvent(CefMouseWheelEvent.WHEEL_UNIT_SCROLL, mouseX, mouseY, amount, modifiers);
        sendMouseWheelEvent(e);
    }

    // Drag & drop
    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        dragContext.startDragging(dragData, mask);
        this.dragTargetDragEnter(dragContext.getDragData(), new Point(x, y), btnMask, dragContext.getMask());
        // Indicates to CEF to not handle the drag event natively
        // reason: native drag handling doesn't work with off screen rendering
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        if (dragContext.updateCursor(operation))
            // If the cursor to display for the drag event changes, then update the cursor
            this.onCursorChange(this, dragContext.getVirtualCursor(dragContext.getActualCursor()));

        super.updateDragCursor(browser, operation);
    }

    // Expose drag & drop functions
    // Overload since the JCEF method requires a browser, which then goes unused
    public void startDragging(CefDragData dragData, int mask, int x, int y) {
        startDragging(dragData, mask, x, y);
    }

    public void finishDragging(int x, int y) {
        dragTargetDrop(new Point(x, y), btnMask);
        dragTargetDragLeave();
        dragContext.stopDragging();
        this.onCursorChange(this, dragContext.getActualCursor());
    }

    public void cancelDrag() {
        dragTargetDragLeave();
        dragContext.stopDragging();
        this.onCursorChange(this, dragContext.getActualCursor());
    }

    // Closing
    public void close() {
        cursorChangeListener.onCursorChange(0);
        super.close(true);
    }

    // Cursor handling
    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        cursorType = dragContext.getVirtualCursor(cursorType);
        cursorChangeListener.onCursorChange(cursorType);
        return super.onCursorChange(browser, cursorType);
    }

    public void setCursor(CefCursorType cursorType) {
        // no-op
    }
}
