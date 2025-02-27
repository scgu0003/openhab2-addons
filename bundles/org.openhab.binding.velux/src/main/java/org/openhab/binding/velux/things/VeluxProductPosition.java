/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.velux.things;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.openhab.binding.velux.VeluxBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <B>Velux</B> product characteristics: Product Position.
 * <P>
 * See <a href=
 * "https://velcdn.azureedge.net/~/media/com/api/klf200/technical%20specification%20for%20klf%20200%20api.pdf#page=110">KLF200
 * Standard Parameter definition</a>
 * <P>
 * Methods in handle this type of information:
 * <UL>
 * <LI>{@link #VeluxProductPosition(int)} to convert a Velux value into the characteristic.</LI>
 * <LI>{@link #VeluxProductPosition(PercentType)} to convert an openHAB value into the characteristic.</LI>
 * <LI>{@link #VeluxProductPosition()} to convert an openHAB STOP value into the characteristic.</LI>
 * <LI>{@link #isValid} to determine whether the characteristic has got a valid value.</LI>
 * <LI>{@link #getPositionAsPercentType()} to convert the characteristic into an openHAB value.</LI>
 * <LI>{@link #getPositionAsVeluxType()} to convert the characteristic into a Velux value.</LI>
 * <LI>{@link #toString} to retrieve a human-readable description of this characteristic.</LI>
 * </UL>
 *
 * @see VeluxKLFAPI
 *
 * @author Guenther Schreiner - initial contribution.
 */
@NonNullByDefault
public class VeluxProductPosition {
    private static final Logger LOGGER = LoggerFactory.getLogger(VeluxProductPosition.class);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Public definition

    public static final VeluxProductPosition UNKNOWN = new VeluxProductPosition();
    public static final int VPP_VELUX_STOP = 0xD200; // NO_UCD (use private)

    // Make sure that the calculation are done as non-integer
    private static final float ONE = 1;

    private static final int VPP_UNKNOWN = 0;

    private static final int VPP_OPENHAB_MIN = 0;
    private static final int VPP_OPENHAB_MAX = 100;
    private static final int VPP_VELUX_MIN = 0x0000;
    private static final int VPP_VELUX_MAX = 0xc800;
    private static final int VPP_VELUX_UNKNOWN = 0xF7FF;

    private static final int VPP_VELUX_PERCENTAGE_MIN = 0xc900;
    private static final int VPP_VELUX_PERCENTAGE_MAX = 0xd0d0;

    // Class internal

    private PercentType position;
    private boolean isValid = false;

    // Constructor

    /**
     * Creation of a Position object to specify a distinct actuator setting.
     *
     * @param position A position as type {@link PercentType} (between 0 and 100).
     */
    public VeluxProductPosition(PercentType position) {
        logger.trace("VeluxProductPosition({} as PercentType) created.", position.intValue());
        this.position = position;
        this.isValid = true;
    }

    /**
     * Creation of a Position object to specify a distinct actuator setting.
     *
     * @param veluxPosition A position as type {@link int} based on the Velux-specific value ranges (between 0x0000 and
     *            0xc800, or 0xD200 for stop).
     */
    public VeluxProductPosition(int veluxPosition) {
        logger.trace("VeluxProductPosition(constructur with {} as veluxPosition) called.", veluxPosition);
        if ((veluxPosition == VPP_VELUX_UNKNOWN) || (veluxPosition == VPP_VELUX_STOP) || (veluxPosition < VPP_VELUX_MIN)
                || (veluxPosition > VPP_VELUX_MAX)) {
            logger.trace("VeluxProductPosition() gives up.");
            this.position = new PercentType(VPP_UNKNOWN);
            this.isValid = false;
        } else {
            float result = (ONE * veluxPosition - VPP_VELUX_MIN) / (VPP_VELUX_MAX - VPP_VELUX_MIN);
            result = Math.round(VPP_OPENHAB_MIN + result * (VPP_OPENHAB_MAX - VPP_OPENHAB_MIN));
            logger.trace("VeluxProductPosition() created with percent-type {}.", (int) result);
            this.position = new PercentType((int) result);
            this.isValid = true;
        }
    }

    /**
     * Creation of a Position object to specify a STOP.
     */
    public VeluxProductPosition() {
        logger.trace("VeluxProductPosition() as STOP position created.");
        this.position = new PercentType(VPP_UNKNOWN);
        this.isValid = false;
    }

    // Class access methods

    public boolean isValid() {
        return this.isValid;
    }

    public PercentType getPositionAsPercentType() {
        return position;
    }

    public PercentType getPositionAsPercentType(boolean toBeInverted) {
        return toBeInverted ? new PercentType(PercentType.HUNDRED.intValue() - position.intValue()) : position;
    }

    public int getPositionAsVeluxType() {
        if (this.isValid) {
            float result = (ONE * position.intValue() - VPP_OPENHAB_MIN) / (VPP_OPENHAB_MAX - VPP_OPENHAB_MIN);
            result = VPP_VELUX_MIN + result * (VPP_VELUX_MAX - VPP_VELUX_MIN);
            return (int) result;
        } else {
            return VPP_VELUX_STOP;
        }
    }

    @Override
    public String toString() {
        if (this.isValid) {
            return String.format("%d", position.intValue());
        } else {
            return new String(VeluxBindingConstants.UNKNOWN);
        }
    }

    // Helper methods

    public static int getRelativePositionAsVeluxType(boolean upwards, PercentType position) { // NO_UCD (unused code)
        LOGGER.trace("getRelativePositionAsVeluxType(upwards={},{}) created.", upwards, position);
        float result = (VPP_VELUX_PERCENTAGE_MAX + VPP_VELUX_PERCENTAGE_MIN) / 2;
        if (upwards) {
            result = result + (ONE * position.intValue() - VPP_OPENHAB_MIN) / (VPP_OPENHAB_MAX - VPP_OPENHAB_MIN)
                    * ((VPP_VELUX_PERCENTAGE_MAX - VPP_VELUX_PERCENTAGE_MIN) / 2);
        } else {
            result = result - (ONE * position.intValue() - VPP_OPENHAB_MIN) / (VPP_OPENHAB_MAX - VPP_OPENHAB_MIN)
                    * ((VPP_VELUX_PERCENTAGE_MAX - VPP_VELUX_PERCENTAGE_MIN) / 2);
        }
        LOGGER.trace("getRelativePositionAsVeluxType() returns {}.", (int) result);
        return (int) result;
    }

}
