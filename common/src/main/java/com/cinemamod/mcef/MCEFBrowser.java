/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package com.cinemamod.mcef;

import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.callback.CefDragData;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * An instance of an "Off-screen rendered" Chromium web browser.
 * Complete with a renderer, keyboard and mouse inputs, optional
 * browser control shortcuts, cursor handling, drag & drop support.
 */
public class MCEFBrowser extends AbstractMCEFBrowser {
    /**
     * The renderer for the browser.
     */
    private final MCEFRenderer renderer;

    /**
     * Used to track when a full repaint should occur.
     */
    private int lastWidth = 0, lastHeight = 0;

    // Data relating to popups and graphics
    // Marked as protected in-case a mod wants to extend MCEFBrowser and override the repaint logic
    protected ByteBuffer graphics;
    protected ByteBuffer popupGraphics;
    protected Rectangle popupSize;
    protected boolean showPopup = false;
    protected boolean popupDrawn = false;

    public MCEFBrowser(MCEFClient client, String url, boolean transparent) {
        super(client.getHandle(), url, transparent, null);
        renderer = new MCEFRenderer(transparent);

        RenderSystem.recordRenderCall(renderer::initialize);
    }

    public MCEFRenderer getRenderer() {
        return renderer;
    }

    // Popups
    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        super.onPopupShow(browser, show);
        showPopup = show;
        if (!show) {
            Minecraft.getInstance().submit(() -> {
                onPaint(browser, false, new Rectangle[]{popupSize}, graphics, lastWidth, lastHeight);
            });
            popupSize = null;
            popupDrawn = false;
            popupGraphics = null;
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        super.onPopupSize(browser, size);
        popupSize = size;
        this.popupGraphics = ByteBuffer.allocateDirect(
                size.width * size.height * 4
        );
    }

    /**
     * Draws any existing popup menu to the browser's graphics
     */
    protected void drawPopup() {
        if (showPopup && popupSize != null && popupDrawn) {
            RenderSystem.bindTexture(renderer.getTextureID());
            if (renderer.isTransparent()) RenderSystem.enableBlend();

            RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, popupSize.width);
            GlStateManager._pixelStore(GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, 0);
            renderer.onPaint(this.popupGraphics, popupSize.x, popupSize.y, popupSize.width, popupSize.height);
        }
    }

    /**
     * Copies data within a rectangle from one buffer to another
     * Used by repaint logic
     *
     * @param srcBuffer the buffer to copy from
     * @param dstBuffer the buffer to copy to
     * @param dirty     the rectangle that needs to be updated
     * @param width     the width of the browser
     * @param height    the height of the browser
     */
    public static void store(ByteBuffer srcBuffer, ByteBuffer dstBuffer, Rectangle dirty, int width, int height) {
        for (int y = dirty.y; y < dirty.height + dirty.y; y++) {
            dstBuffer.position((y * width + dirty.x) * 4);
            srcBuffer.position((y * width + dirty.x) * 4);
            srcBuffer.limit(dirty.width * 4 + (y * width + dirty.x) * 4);
            dstBuffer.put(srcBuffer);
            srcBuffer.position(0).limit(srcBuffer.capacity());
        }
        dstBuffer.position(0).limit(dstBuffer.capacity());
    }

    // Graphics
    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (!popup && (width != lastWidth || height != lastHeight)) {
            // Copy buffer
            graphics = ByteBuffer.allocateDirect(buffer.capacity());
            graphics.position(0).limit(graphics.capacity());
            graphics.put(buffer);
            graphics.position(0);
            buffer.position(0);

            // Draw
            renderer.onPaint(buffer, width, height);
            lastWidth = width;
            lastHeight = height;
        } else {
            // Don't update graphics if the renderer is not initialized
            if (renderer.getTextureID() == 0) return;

            // Update sub-rects
            if (!popup) {
                // Graphics will be updated later if it's a popup
                RenderSystem.bindTexture(renderer.getTextureID());
                if (renderer.isTransparent()) RenderSystem.enableBlend();
                RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, width);
            } else popupDrawn = true;

            for (Rectangle dirtyRect : dirtyRects) {
                // Check that the popup isn't being cleared from the image
                if (buffer != graphics)
                    // Due to how CEF handles popups, the graphics of the popup and the graphics of the browser itself need to be stored separately
                    store(buffer, popup ? popupGraphics : graphics, dirtyRect, width, height);

                // Graphics will be updated later if it's a popup
                if (!popup) {
                    // Upload to the GPU
                    GlStateManager._pixelStore(GL_UNPACK_SKIP_PIXELS, dirtyRect.x);
                    GlStateManager._pixelStore(GL_UNPACK_SKIP_ROWS, dirtyRect.y);
                    renderer.onPaint(buffer, dirtyRect.x, dirtyRect.y, dirtyRect.width, dirtyRect.height);
                }
            }
        }

        // Upload popup to GPU, must be fully drawn every time paint is called
        drawPopup();
    }

    public void resize(int width, int height) {
        browser_rect_.setBounds(0, 0, width, height);
        wasResized(width, height);
    }

    // Closing
    public void close() {
        renderer.cleanup();
        super.close(true);
    }

    @Override
    protected void finalize() throws Throwable {
        RenderSystem.recordRenderCall(renderer::cleanup);
        super.finalize();
    }

    @Override
    public void setCursor(CefCursorType cursorType) {
        if (cursorType == CefCursorType.NONE) {
            org.lwjgl.glfw.GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        } else {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), MCEF.getGLFWCursorHandle(cursorType));
        }
    }
}
