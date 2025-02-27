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
package org.openhab.binding.velux.handler;

import static org.openhab.binding.velux.VeluxBindingConstants.CHANNEL_ACTUATOR_POSITION;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.velux.VeluxBindingProperties;
import org.openhab.binding.velux.bridge.VeluxBridgeRunProductCommand;
import org.openhab.binding.velux.bridge.common.GetProduct;
import org.openhab.binding.velux.things.VeluxProduct;
import org.openhab.binding.velux.things.VeluxProductPosition;
import org.openhab.binding.velux.things.VeluxProductSerialNo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <B>Channel-specific retrieval and modification.</B>
 * <P>
 * This class implements the Channel <B>position</B> of the Thing <B>actuator</B>:
 * <UL>
 * <LI><I>Velux</I> <B>bridge</B> &rarr; <B>OpenHAB</B>:
 * <P>
 * Information retrieval by method {@link #handleRefresh}.</LI>
 * </UL>
 * <UL>
 * <LI><B>OpenHAB</B> Event Bus &rarr; <I>Velux</I> <B>bridge</B>
 * <P>
 * Sending commands and value updates by method {@link #handleCommand}.</LI>
 * </UL>
 *
 * @author Guenther Schreiner - Initial contribution.
 */
@NonNullByDefault
final class ChannelActuatorPosition extends VeluxChannelHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelActuatorPosition.class);

    /*
     * ************************
     * ***** Constructors *****
     */

    // Suppress default constructor for non-instantiability

    private ChannelActuatorPosition() {
        throw new AssertionError();
    }

    /**
     * Communication method to retrieve information to update the channel value.
     *
     * @param channelUID The item passed as type {@link ChannelUID} for which a refresh is intended.
     * @param channelId The same item passed as type {@link String} for which a refresh is intended.
     * @param thisBridgeHandler The Velux bridge handler with a specific communication protocol which provides
     *            information for this channel.
     * @return newState The value retrieved for the passed channel, or <I>null</I> in case if there is no (new) value.
     */
    static @Nullable State handleRefresh(ChannelUID channelUID, String channelId,
            VeluxBridgeHandler thisBridgeHandler) {
        LOGGER.debug("handleRefresh({},{},{}) called.", channelUID, channelId, thisBridgeHandler);
        State newState = null;
        do { // just for common exit
            if (thisBridgeHandler.bridgeParameters.actuators.autoRefresh(thisBridgeHandler.thisBridge)) {
                LOGGER.trace("handleRefresh(): there are some existing products.");
            }
            if (!ThingProperty.exists(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_SERIALNUMBER)) {
                LOGGER.trace("handleRefresh(): aborting processing as {} is not set.",
                        VeluxBindingProperties.PROPERTY_ACTUATOR_SERIALNUMBER);
                break;
            }
            String actuatorSerial = (String) ThingProperty.getValue(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_SERIALNUMBER);
            LOGGER.trace("handleRefresh(): actuatorSerial={}", actuatorSerial);

            // Handle value inversion
            boolean propertyInverted = false;
            if (ThingProperty.exists(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_INVERTED)) {
                propertyInverted = (boolean) ThingProperty.getValue(thisBridgeHandler, channelUID,
                        VeluxBindingProperties.PROPERTY_ACTUATOR_INVERTED);
            }
            boolean isInverted = propertyInverted || VeluxProductSerialNo.indicatesRevertedValues(actuatorSerial);
            LOGGER.trace("handleRefresh(): isInverted={}.", isInverted);
            actuatorSerial = VeluxProductSerialNo.cleaned(actuatorSerial);

            if (!thisBridgeHandler.bridgeParameters.actuators.getChannel().existingProducts
                    .isRegistered(actuatorSerial)) {
                LOGGER.info("handleRefresh(): cannot work on unknown actuator with serial {}.", actuatorSerial);
                break;
            }
            LOGGER.trace("handleRefresh(): fetching actuator for {}.", actuatorSerial);
            VeluxProduct thisProduct = thisBridgeHandler.bridgeParameters.actuators.getChannel().existingProducts
                    .get(actuatorSerial);
            LOGGER.trace("handleRefresh(): found actuator {}.", thisProduct);

            GetProduct bcp = thisBridgeHandler.thisBridge.bridgeAPI().getProduct();
            if (bcp == null) {
                LOGGER.trace("handleRefresh(): aborting processing as handler is null.");
                break;
            }
            bcp.setProductId(thisProduct.getBridgeProductIndex().toInt());
            if (thisBridgeHandler.thisBridge.bridgeCommunicate(bcp)) {
                thisProduct = bcp.getProduct();
                if (bcp.isCommunicationSuccessful()) {
                    try {
                        VeluxProductPosition position = new VeluxProductPosition(thisProduct.getCurrentPosition());
                        if (position.isValid()) {
                            PercentType positionAsPercent = position.getPositionAsPercentType(isInverted);
                            LOGGER.trace("handleRefresh(): found actuator at level {}.", positionAsPercent);
                            newState = positionAsPercent;
                        } else {
                            LOGGER.trace("handleRefresh(): level of actuator is unknown.");
                        }
                    } catch (Exception e) {
                        LOGGER.warn("handleRefresh(): getProducts() exception: {}.", e.getMessage());
                    }
                }
            }
        } while (false); // common exit
        LOGGER.trace("handleRefresh() returns {}.", newState);
        return newState;
    }

    /**
     * Communication method to update the real world according to the passed channel value (or command).
     *
     * @param channelUID The item passed as type {@link ChannelUID} for which to following command is addressed to.
     * @param channelId The same item passed as type {@link String} for which a refresh is intended.
     * @param command The command passed as type {@link Command} for the mentioned item.
     * @param thisBridgeHandler The Velux bridge handler with a specific communication protocol which provides
     *            information for this channel.
     * @return newValue ...
     */
    static @Nullable Command handleCommand(ChannelUID channelUID, String channelId, Command command,
            VeluxBridgeHandler thisBridgeHandler) {
        LOGGER.debug("handleCommand({},{},{},{}) called.", channelUID, channelId, command, thisBridgeHandler);
        Command newValue = null;
        do { // just for common exit
            assert thisBridgeHandler.bridgeParameters.actuators != null : "VeluxBridgeHandler.bridgeParameters.actuators not initialized.";
            if (thisBridgeHandler.bridgeParameters.actuators.autoRefresh(thisBridgeHandler.thisBridge)) {
                LOGGER.trace("handleCommand(): there are some existing products.");
            }
            if (!ThingProperty.exists(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_SERIALNUMBER)) {
                LOGGER.trace("handleCommand(): aborting processing as actuatorSerial is not set.");
                break;
            }
            String actuatorSerial = (String) ThingProperty.getValue(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_SERIALNUMBER);
            LOGGER.trace("handleCommand(): actuatorSerial={}", actuatorSerial);

            // Handle value inversion
            boolean propertyInverted = false;
            if (ThingProperty.exists(thisBridgeHandler, channelUID,
                    VeluxBindingProperties.PROPERTY_ACTUATOR_INVERTED)) {
                propertyInverted = (boolean) ThingProperty.getValue(thisBridgeHandler, channelUID,
                        VeluxBindingProperties.PROPERTY_ACTUATOR_INVERTED);
            }
            boolean isInverted = propertyInverted || VeluxProductSerialNo.indicatesRevertedValues(actuatorSerial);
            LOGGER.trace("handleCommand(): isInverted={}.", isInverted);
            actuatorSerial = VeluxProductSerialNo.cleaned(actuatorSerial);

            if (!thisBridgeHandler.bridgeParameters.actuators.getChannel().existingProducts
                    .isRegistered(actuatorSerial)) {
                LOGGER.info("handleCommand({},{}): cannot work on unknown actuator: {}.", channelUID.getAsString(),
                        command, actuatorSerial);
                break;
            }
            LOGGER.trace("handleCommand(): fetching product for {}.", actuatorSerial);
            VeluxProduct thisProduct = thisBridgeHandler.bridgeParameters.actuators.getChannel().existingProducts
                    .get(actuatorSerial);
            LOGGER.trace("handleCommand(): found product {}.", thisProduct);

            VeluxProductPosition targetLevel = VeluxProductPosition.UNKNOWN;
            if (channelId.equals(CHANNEL_ACTUATOR_POSITION)) {
                if ((command instanceof UpDownType) && ((UpDownType) command == UpDownType.UP)) {
                    LOGGER.trace("handleCommand(): found UP command.");
                    targetLevel = isInverted ? new VeluxProductPosition(PercentType.HUNDRED)
                            : new VeluxProductPosition(PercentType.ZERO);
                } else if ((command instanceof UpDownType) && ((UpDownType) command == UpDownType.DOWN)) {
                    LOGGER.trace("handleCommand(): found DOWN command.");
                    targetLevel = isInverted ? new VeluxProductPosition(PercentType.ZERO)
                            : new VeluxProductPosition(PercentType.HUNDRED);
                } else if ((command instanceof StopMoveType) && ((StopMoveType) command == StopMoveType.STOP)) {
                    LOGGER.trace("handleCommand(): found STOP command.");
                    targetLevel = new VeluxProductPosition();
                } else if (command instanceof PercentType) {
                    LOGGER.trace("handleCommand(): found command of type PercentType.");
                    PercentType ptCommand = (PercentType) command;
                    if (isInverted) {
                        ptCommand = new PercentType(PercentType.HUNDRED.intValue() - ptCommand.intValue());
                    }
                    LOGGER.trace("handleCommand(): found command to set level to {}.", ptCommand);
                    targetLevel = new VeluxProductPosition(ptCommand);
                } else {
                    LOGGER.info("handleCommand({},{}): ignoring command.", channelUID.getAsString(), command);
                    break;
                }
            } else {
                if ((command instanceof OnOffType) && ((OnOffType) command == OnOffType.ON)) {
                    LOGGER.trace("handleCommand(): found ON command.");
                    targetLevel = isInverted ? new VeluxProductPosition(PercentType.HUNDRED)
                            : new VeluxProductPosition(PercentType.ZERO);
                } else if ((command instanceof OnOffType) && ((OnOffType) command == OnOffType.OFF)) {
                    LOGGER.trace("handleCommand(): found OFF command.");
                    targetLevel = isInverted ? new VeluxProductPosition(PercentType.ZERO)
                            : new VeluxProductPosition(PercentType.HUNDRED);
                } else {
                    LOGGER.info("handleCommand({},{}): ignoring command.", channelUID.getAsString(), command);
                    break;
                }
            }
            LOGGER.debug("handleCommand(): sending command with target level {}.", targetLevel);
            new VeluxBridgeRunProductCommand().sendCommand(thisBridgeHandler.thisBridge,
                    thisProduct.getBridgeProductIndex().toInt(), targetLevel);
            LOGGER.trace("handleCommand(): The new shutter level will be send through the home monitoring events.");

            if (thisBridgeHandler.bridgeParameters.actuators.autoRefresh(thisBridgeHandler.thisBridge)) {
                LOGGER.trace("handleCommand(): position of actuators are updated.");
            }
        } while (false); // common exit
        return newValue;
    }

}
