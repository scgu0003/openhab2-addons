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
package org.openhab.binding.velux.bridge;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.velux.bridge.common.GetProduct;
import org.openhab.binding.velux.bridge.common.GetProducts;
import org.openhab.binding.velux.things.VeluxExistingProducts;
import org.openhab.binding.velux.things.VeluxKLFAPI;
import org.openhab.binding.velux.things.VeluxProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link VeluxBridgeActuators} represents a complete set of transactions
 * for retrieving of any available products into a structure {@link #channel}
 * defined on the <B>Velux</B> bridge.
 * <P>
 * It provides the methods:
 * <UL>
 * <LI>{@link #getProducts} for retrieval of information from the bridge.
 * <LI>{@link #getChannel} for accessing the retrieved information.
 * <LI>{@link #autoRefresh} for retrieval of information for supporting the update the corresponding openHAB items.
 * </UL>
 *
 * @see VeluxProduct
 * @see VeluxExistingProducts
 * @see VeluxBridgeProvider
 *
 * @author Guenther Schreiner - Initial contribution
 */
@NonNullByDefault
public class VeluxBridgeActuators {
    private final Logger logger = LoggerFactory.getLogger(VeluxBridgeActuators.class);

    // Configuration constants

    /**
     * Limitation of Discovery on parts of the System table
     *
     * Whereas the parameter {@link org.openhab.binding.velux.things.VeluxKLFAPI#KLF_SYSTEMTABLE_MAX} represents the
     * maximum size of the system table in general, for speed up of the discovery process
     * a binding-internal limitation of number of possible devices is introduced.
     */
    private static final int VELUXBINDING_SYSTEMTABLE_MAX = 16;

    // Type definitions, class-internal variables

    /**
     * Actuator information consisting of:
     * <ul>
     * <li>isRetrieved (boolean),
     * <li>existingProducts ({@link VeluxExistingProducts}).
     * </ul>
     */
    @NonNullByDefault
    public class Channel {
        public VeluxExistingProducts existingProducts = new VeluxExistingProducts();
    }

    private Channel channel;

    // Constructor methods

    /**
     * Constructor.
     * <P>
     * Initializes the internal data structure {@link #channel} of Velux actuators/products,
     * which is publicly accessible via the method {@link #getChannel()}.
     */
    public VeluxBridgeActuators() {
        logger.trace("VeluxBridgeActuators(constructor) called.");
        channel = new Channel();
        logger.trace("VeluxBridgeActuators(constructor) done.");
    }

    // Class access methods

    /**
     * Provide access to the internal structure of actuators/products.
     *
     * @return a channel describing the overall actuator situation.
     */
    public Channel getChannel() {
        return channel;
    }

    /**
     * Login into bridge, retrieve all products and logout from bridge based
     * on a well-prepared environment of a {@link VeluxBridgeProvider}. The results
     * are stored within {@link org.openhab.binding.velux.things.VeluxExistingProducts
     * VeluxExistingProducts}.
     *
     * @param bridge Initialized Velux bridge (communication) handler.
     * @return true if successful, and false otherwise.
     */
    @SuppressWarnings("null")
    public boolean getProducts(VeluxBridge bridge) {
        logger.trace("getProducts() called.");

        GetProducts bcp = bridge.bridgeAPI().getProducts();
        if (!bridge.bridgeInstance.veluxBridgeConfiguration().isBulkRetrievalEnabled || (bcp == null)) {
            logger.trace("getProducts() working on step-by-step retrieval.");
            GetProduct bcpSbS = bridge.bridgeAPI().getProduct();
            for (int nodeId = 0; nodeId < VeluxKLFAPI.KLF_SYSTEMTABLE_MAX
                    && nodeId < VELUXBINDING_SYSTEMTABLE_MAX; nodeId++) {
                logger.trace("getProducts() {}.", new String(new char[80]).replace('\0', '*'));
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    logger.trace("io() wait interrupted.");
                }
                logger.trace("getProducts() working on product number {}.", nodeId);
                bcpSbS.setProductId(nodeId);
                if ((bridge.bridgeCommunicate(bcpSbS)) && (bcpSbS.isCommunicationSuccessful())) {
                    VeluxProduct veluxProduct = bcpSbS.getProduct();
                    if (bcpSbS.isCommunicationSuccessful()) {
                        logger.debug("getProducts() found product {}.", veluxProduct);
                        if (!channel.existingProducts.isRegistered(veluxProduct)) {
                            channel.existingProducts.register(veluxProduct);
                        }
                    }
                }
            }
            logger.debug("getProducts() finally has found products {}.", channel.existingProducts);
            return true;
        } else {
            logger.trace("getProducts() working on bulk retrieval.");
            if ((bridge.bridgeCommunicate(bcp)) && (bcp.isCommunicationSuccessful())) {
                for (VeluxProduct product : bcp.getProducts()) {
                    logger.trace("getProducts() found product {} (type {}).", product.getProductName(),
                            product.getProductType());
                    if (!channel.existingProducts.isRegistered(product)) {
                        logger.debug("getProducts() storing new product {}.", product);
                        channel.existingProducts.register(product);
                    } else {
                        logger.debug("getProducts() storing updates for product {}.", product);
                        channel.existingProducts.update(product);
                    }
                }
                logger.debug("getProducts() finally has found products {}.", channel.existingProducts);
                return true;
            } else {
                logger.trace("getProducts() finished with failure.");
                return false;
            }
        }
    }

    /**
     * In case of an empty list of recognized products, the method will
     * initiate a product retrieval using {@link #getProducts(VeluxBridge)}.
     *
     * @param bridge Initialized Velux bridge (communication) handler.
     * @return true if at least one product was found, and false otherwise.
     */
    public boolean autoRefresh(VeluxBridge bridge) {
        logger.trace("autoRefresh() called.");
        if (channel.existingProducts.getNoMembers() == 0) {
            logger.trace("autoRefresh(): is about to fetch existing products.");
            getProducts(bridge);
        }
        return (channel.existingProducts.getNoMembers() > 0);
    }

}
