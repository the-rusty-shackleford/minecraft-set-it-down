/*
 * Set It Down - put any item down on any block face.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * The pure layer: where on a block's face a display may sit ({@link Face}, {@link FacePlacement}),
 * how it turns and tips ({@link Orientation}), how far a turned, tipped pile must stand off the
 * surface and how armour stands or hangs ({@link Pose}, over {@link Affine} and {@link Box}), how a
 * pile or a cluster is laid out ({@link StackLayout}), what a click on a display does
 * ({@link Clicks}), whether a new item joins a display or has no room ({@link Placement}) and how
 * big a display's box is ({@link Hitbox}). Compiled against the JDK alone, so a Minecraft import
 * here is a compile error.
 */
package com.chunkworks.setitdown.domain;
